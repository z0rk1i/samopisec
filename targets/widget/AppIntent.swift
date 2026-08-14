import AppIntents
import WidgetKit

struct TapButtonIntent: AppIntent {
  static var title: LocalizedStringResource = "Нажать кнопку"
  static var description = IntentDescription("Записывает нажатие кнопки Samopisec.")
  static var isDiscoverable = false

  @Parameter(title: "Button ID", default: "")
  var buttonId: String

  static func tap(_ id: String) -> TapButtonIntent {
    let parameter = IntentParameter<String>(title: "Button ID")
    parameter.wrappedValue = id
    return TapButtonIntent(buttonId: parameter)
  }

  func perform() async throws -> some IntentResult {
    WidgetStore.appendDatapoint(buttonId: buttonId)
    WidgetCenter.shared.reloadAllTimelines()
    return .result()
  }
}
