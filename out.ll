; ModuleID = 'sysy_module'
source_filename = "sysy_module"

define i32 @main() {
mainEntry:
  %a = alloca i32, align 4
  store i32 2, i32* %a, align 4
  %0 = load i32, i32* %a, align 4
  ret i32 %0
}
