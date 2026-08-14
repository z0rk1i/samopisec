import WidgetKit
import SwiftUI
import AppIntents

// MARK: - Shared data access (App Group container)

enum WidgetStore {
  static let appGroup = "group.com.z0rk1.samopisec"

  static var containerURL: URL? {
    FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
  }

  static func configURL() -> URL? {
    containerURL?.appendingPathComponent("config.json")
  }

  static func datapointsURL() -> URL? {
    containerURL?.appendingPathComponent("datapoints.jsonl")
  }

  struct ButtonInfo: Identifiable {
    let id: String
    let label: String
    let colorHex: String
    let count: Int
  }

  static func loadButtons() -> [ButtonInfo] {
    guard let url = configURL(),
          let data = try? Data(contentsOf: url),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let arr = json["buttons"] as? [[String: Any]] else { return [] }
    let counts = todayCounts()
    return arr.prefix(6).compactMap { b in
      guard let id = b["id"] as? String else { return nil }
      return ButtonInfo(
        id: id,
        label: b["label"] as? String ?? "?",
        colorHex: b["color"] as? String ?? "#1976D2",
        count: counts[id] ?? 0
      )
    }
  }

  static func todayCounts() -> [String: Int] {
    guard let url = datapointsURL(),
          let text = try? String(contentsOf: url, encoding: .utf8) else { return [:] }
    let start = startOfTodayMillis()
    var counts: [String: Int] = [:]
    for line in text.split(separator: "\n") {
      guard let data = line.data(using: .utf8),
            let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let ts = obj["ts"] as? NSNumber,
            ts.doubleValue >= start,
            let id = obj["button-id"] as? String,
            !id.isEmpty else { continue }
      counts[id, default: 0] += 1
    }
    return counts
  }

  static func startOfTodayMillis() -> TimeInterval {
    let cal = Calendar.current
    let comps = cal.dateComponents([.year, .month, .day], from: Date())
    return (cal.date(from: comps)?.timeIntervalSince1970 ?? 0) * 1000
  }

  static func appendDatapoint(buttonId: String) {
    guard let url = datapointsURL() else { return }
    let dp: [String: Any] = [
      "id": UUID().uuidString,
      "button-id": buttonId,
      "ts": Date().timeIntervalSince1970 * 1000
    ]
    let line = ((try? JSONSerialization.data(withJSONObject: dp)) ?? Data()) + Data("\n".utf8)
    if let handle = try? FileHandle(forWritingTo: url) {
      defer { try? handle.close() }
      handle.seekToEndOfFile()
      handle.write(line)
    } else {
      try? line.write(to: url, options: .atomic)
    }
  }
}

// MARK: - Timeline entry

struct SimpleEntry: TimelineEntry {
  let date: Date
  let buttons: [WidgetStore.ButtonInfo]
  let total: Int
}

struct Provider: TimelineProvider {
  func placeholder(in context: Context) -> SimpleEntry {
    SimpleEntry(
      date: .now,
      buttons: [WidgetStore.ButtonInfo(id: "1", label: "Пример", colorHex: "#1976D2", count: 3)],
      total: 3
    )
  }

  func getSnapshot(in context: Context, completion: @escaping (SimpleEntry) -> Void) {
    completion(makeEntry())
  }

  func getTimeline(in context: Context, completion: @escaping (Timeline<SimpleEntry>) -> Void) {
    let entry = makeEntry()
    // Пересчитывать на границе суток, чтобы счётчик «Сегодня» сбрасывался.
    let nextMidnight = Calendar.current.nextDate(
      after: .now,
      matching: DateComponents(hour: 0, minute: 0, second: 0),
      matchingPolicy: .nextTime
    ) ?? .now.addingTimeInterval(3600)
    completion(Timeline(entries: [entry], policy: .after(nextMidnight)))
  }

  private func makeEntry() -> SimpleEntry {
    let buttons = WidgetStore.loadButtons()
    let total = buttons.reduce(0) { $0 + $1.count }
    return SimpleEntry(date: .now, buttons: buttons, total: total)
  }
}

// MARK: - View

struct SamopisecEntryView: View {
  @Environment(\.widgetFamily) var family
  var entry: SimpleEntry

  private let columns = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack {
        Text(entry.total > 0 ? "Сегодня: \(entry.total)" : "Samopisec")
          .font(.headline)
          .foregroundStyle(.primary)
        Spacer()
      }
      if entry.buttons.isEmpty {
        Spacer()
        Text("Откройте приложение\nи добавьте кнопки")
          .font(.subheadline)
          .foregroundStyle(.secondary)
          .multilineTextAlignment(.center)
          .frame(maxWidth: .infinity)
        Spacer()
      } else {
        LazyVGrid(columns: columns, spacing: 8) {
          ForEach(entry.buttons) { b in
            Button(intent: TapButtonIntent.tap(b.id)) {
              buttonCell(b)
            }
          }
        }
        Spacer(minLength: 0)
      }
    }
    .containerBackground(.fill.tertiary, for: .widget)
  }

  private func buttonCell(_ b: WidgetStore.ButtonInfo) -> some View {
    VStack(spacing: 2) {
      Text(b.label)
        .font(.caption)
        .lineLimit(1)
        .minimumScaleFactor(0.8)
        .foregroundStyle(.white)
      Text("\(b.count)")
        .font(.title3.bold())
        .foregroundStyle(.white)
    }
    .frame(maxWidth: .infinity, minHeight: 44)
    .background(Color(hex: b.colorHex))
    .clipShape(RoundedRectangle(cornerRadius: 8))
  }
}

extension Color {
  init(hex: String) {
    var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
    if s.hasPrefix("#") { s.removeFirst() }
    var value: UInt64 = 0
    Scanner(string: s).scanHexInt64(&value)
    let r = Double((value >> 16) & 0xFF) / 255
    let g = Double((value >> 8) & 0xFF) / 255
    let b = Double(value & 0xFF) / 255
    self.init(red: r, green: g, blue: b)
  }
}

// MARK: - Widget

struct SamopisecWidget: Widget {
  let kind: String = "SamopisecWidget"

  var body: some WidgetConfiguration {
    StaticConfiguration(kind: kind, provider: Provider()) { entry in
      SamopisecEntryView(entry: entry)
    }
    .configurationDisplayName("Samopisec")
    .description("Кнопки быстрого учёта")
    .supportedFamilies([.systemSmall, .systemMedium])
  }
}
