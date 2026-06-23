; ModuleID = 'sysy_module'
source_filename = "sysy_module"

@count = global i32 0
@n = global i32 3

declare i32 @getint()

declare void @putint(i32)

define void @hanoi(i32 %0, i32 %1, i32 %2, i32 %3) {
hanoiEntry:
  %n_addr = alloca i32, align 4
  store i32 %0, i32* %n_addr, align 4
  %source_addr = alloca i32, align 4
  store i32 %1, i32* %source_addr, align 4
  %target_addr = alloca i32, align 4
  store i32 %2, i32* %target_addr, align 4
  %auxiliary_addr = alloca i32, align 4
  store i32 %3, i32* %auxiliary_addr, align 4
  %4 = load i32, i32* %n_addr, align 4
  %cmptmp = icmp eq i32 %4, 1
  br i1 %cmptmp, label %if_true, label %if_next

if_true:                                          ; preds = %hanoiEntry
  %5 = load i32, i32* @count, align 4
  %addtmp = add i32 %5, 1
  store i32 %addtmp, i32* @count, align 4
  ret void

if_next:                                          ; preds = %hanoiEntry
  %6 = load i32, i32* %n_addr, align 4
  %subtmp = sub i32 %6, 1
  %7 = load i32, i32* %source_addr, align 4
  %8 = load i32, i32* %auxiliary_addr, align 4
  %9 = load i32, i32* %target_addr, align 4
  call void @hanoi(i32 %subtmp, i32 %7, i32 %8, i32 %9)
  %10 = load i32, i32* @count, align 4
  %addtmp1 = add i32 %10, 1
  store i32 %addtmp1, i32* @count, align 4
  %11 = load i32, i32* %n_addr, align 4
  %subtmp2 = sub i32 %11, 1
  %12 = load i32, i32* %auxiliary_addr, align 4
  %13 = load i32, i32* %target_addr, align 4
  %14 = load i32, i32* %source_addr, align 4
  call void @hanoi(i32 %subtmp2, i32 %12, i32 %13, i32 %14)
  ret void
}

define i32 @main() {
mainEntry:
  %0 = load i32, i32* @n, align 4
  call void @hanoi(i32 %0, i32 1, i32 3, i32 2)
  %1 = load i32, i32* @count, align 4
  ret i32 %1
  ret i32 0
}
