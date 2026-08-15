import Foundation

@main
struct WidgetConfigTestsMain {
  static func main() {
    run()
  }
}

func run() {
  var failures = 0

func check(_ cond: Bool, _ msg: String) {
  if cond {
    print("PASS: \(msg)")
  } else {
    failures += 1
    print("FAIL: \(msg)")
  }
}

// Валидные кнопки сохраняются.
let valid: [String: Any] = ["buttons": [
  ["id": "tea", "label": "Чай", "color": "#1e88e5"],
  ["id": "coffee", "label": "Кофе", "color": "#8e24aa"]
]]
let r1 = WidgetConfig.parseButtons(from: valid)
check(r1.count == 2, "valid buttons kept")
check(r1[0].id == "tea", "first button id")
check(r1[0].label == "Чай", "first button label")
check(r1[0].colorHex == "#1e88e5", "first button color")

// Кнопка без id отбрасывается.
let r2 = WidgetConfig.parseButtons(from: ["buttons": [["label": "no-id"], ["id": "ok", "label": "Ок"]]])
check(r2.count == 1 && r2[0].id == "ok", "button without id skipped")

// Кнопка без label отбрасывается.
let r3 = WidgetConfig.parseButtons(from: ["buttons": [["id": "x"], ["id": "y", "label": "Y"]]])
check(r3.count == 1 && r3[0].id == "y", "button without label skipped")

// Не-словарные записи отбрасываются.
let r4 = WidgetConfig.parseButtons(from: ["buttons": ["garbage", ["id": "ok", "label": "Ок"]]])
check(r4.count == 1 && r4[0].id == "ok", "non-dict entry skipped")

// Больше maxButtons — обрезается до 6.
let many = (0..<8).map { ["id": "b\($0)", "label": "B\($0)"] }
check(WidgetConfig.parseButtons(from: ["buttons": many]).count == 6, "capped at maxButtons")

// Пустой список и отсутствие ключа buttons.
check(WidgetConfig.parseButtons(from: ["buttons": [] as [Any]]).isEmpty, "empty buttons -> empty")
check(WidgetConfig.parseButtons(from: [:]).isEmpty, "no buttons key -> empty")

// Цвет по умолчанию при отсутствии.
let r5 = WidgetConfig.parseButtons(from: ["buttons": [["id": "a", "label": "A"]]])
check(r5[0].colorHex == ButtonSpec.defaultColor, "default color applied")

if failures == 0 {
  print("ALL \(6 + 3) CHECKS PASSED")
} else {
  print("\(failures) FAILURES")
  exit(1)
}
}