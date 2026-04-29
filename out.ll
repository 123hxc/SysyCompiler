; ModuleID = 'sysy_module'
source_filename = "sysy_module"

@g_var = global i32 2

define i32 @main() {
mainEntry:
  %a = alloca i32, align 4
  store i32 1, i32* %a, align 4
  %0 = load i32, i32* %a, align 4
  %1 = load i32, i32* @g_var, align 4
  %addtmp = add i32 %0, %1
  ret i32 %addtmp
}
