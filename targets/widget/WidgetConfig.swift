import Foundation

struct ButtonSpec: Identifiable {
  let id: String
  let label: String
  let colorHex: String

  static let defaultColor = "#1976D2"
}

/// Чистый парсинг конфига кнопок виджета — без WidgetKit-зависимостей, покрыт тестами.
enum WidgetConfig {
  static let maxButtons = 6

  static func parseButtons(from json: [String: Any]) -> [ButtonSpec] {
    guard let arr = json["buttons"] as? [Any] else { return [] }
    return arr.prefix(maxButtons).compactMap { b in
      guard let dict = b as? [String: Any],
            let id = dict["id"] as? String,
            let label = dict["label"] as? String,
            !id.trimmingCharacters(in: .whitespaces).isEmpty,
            !label.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
      return ButtonSpec(
        id: id,
        label: label,
        colorHex: dict["color"] as? String ?? ButtonSpec.defaultColor
      )
    }
  }

  static func parseButtons(from data: Data) -> [ButtonSpec] {
    guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
    return parseButtons(from: json)
  }
}