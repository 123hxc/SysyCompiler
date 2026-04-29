; ModuleID = 'sysy_module'
source_filename = "sysy_module"

define i32 @f(i32 %0) {
fEntry:
  %i_addr = alloca i32, align 4
  store i32 %0, i32* %i_addr, align 4
  %1 = load i32, i32* %i_addr, align 4
  ret i32 %1
}

define i32 @main() {
mainEntry:
  %a = alloca i32, align 4
  store i32 1, i32* %a, align 4
  %0 = load i32, i32* %a, align 4
  %1 = call i32 @f(i32 %0)
  ret i32 %1
}
