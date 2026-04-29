; ModuleID = 'sysy_module'
source_filename = "sysy_module"

@a = global i32 0
@count = global i32 0

define i32 @main() {
mainEntry:
  br label %while_cond

while_cond:                                       ; preds = %if_next, %mainEntry
  %0 = load i32, i32* @a, align 4
  %cmptmp = icmp sle i32 %0, 0
  br i1 %cmptmp, label %while_body, label %while_next

while_body:                                       ; preds = %while_cond
  %1 = load i32, i32* @a, align 4
  %subtmp = sub i32 %1, 1
  store i32 %subtmp, i32* @a, align 4
  %2 = load i32, i32* @count, align 4
  %addtmp = add i32 %2, 1
  store i32 %addtmp, i32* @count, align 4
  %3 = load i32, i32* @a, align 4
  %cmptmp1 = icmp slt i32 %3, -20
  br i1 %cmptmp1, label %if_true, label %if_next

if_true:                                          ; preds = %while_body
  br label %while_next
  br label %if_next

if_next:                                          ; preds = %if_true, %while_body
  br label %while_cond

while_next:                                       ; preds = %if_true, %while_cond
  %4 = load i32, i32* @count, align 4
  ret i32 %4
}
