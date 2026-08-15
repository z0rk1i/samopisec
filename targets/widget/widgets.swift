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
  }

  static func loadButtons() -> [ButtonInfo] {
    guard let url = configURL(),
          let data = try? Data(contentsOf: url),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let arr = json["buttons"] as? [[String: Any]] else { return [] }
    return arr.prefix(6).compactMap { b in
      guard let id = b["id"] as? String,
            let label = b["label"] as? String,
            !id.trimmingCharacters(in: .whitespaces).isEmpty,
            !label.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
      return ButtonInfo(
        id: id,
        label: label,
        colorHex: b["color"] as? String ?? "#1976D2"
      )
    }
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
}

struct Provider: TimelineProvider {
  func placeholder(in context: Context) -> SimpleEntry {
    SimpleEntry(
      date: .now,
      buttons: [WidgetStore.ButtonInfo(id: "1", label: "Пример", colorHex: "#1976D2")]
    )
  }

  func getSnapshot(in context: Context, completion: @escaping (SimpleEntry) -> Void) {
    completion(makeEntry())
  }

  func getTimeline(in context: Context, completion: @escaping (Timeline<SimpleEntry>) -> Void) {
    let entry = makeEntry()
    // Пересчёт не реже раза в час, чтобы подхватывать изменения конфигурации.
    completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(3600))))
  }

  private func makeEntry() -> SimpleEntry {
    SimpleEntry(date: .now, buttons: WidgetStore.loadButtons())
  }
}

// MARK: - View

struct SamopisecEntryView: View {
  @Environment(\.widgetFamily) var family
  var entry: SimpleEntry

  private let columns = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
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
            .accessibilityLabel(b.label)
          }
        }
        Spacer(minLength: 0)
      }
    }
    .containerBackground(.fill.tertiary, for: .widget)
  }

  private func buttonCell(_ b: WidgetStore.ButtonInfo) -> some View {
    Text(b.label)
      .font(.headline)
      .lineLimit(1)
      .minimumScaleFactor(0.8)
      .foregroundStyle(.white)
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
