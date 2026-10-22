.text
.globl main

main:
    addi $t0, $zero, 5
    fake $t1, $t0, 10
    syscall