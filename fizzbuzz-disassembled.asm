.data
data_start:
    .byte 70, 105, 122, 122, 0, 66, 117, 122, 122, 0, 70, 105, 122, 122, 66, 117, 122, 122, 0

.text
.globl main

main:
    addiu $t0, $zero, 1
    addiu $t1, $zero, 100
L_00400008:
    addiu $at, $zero, 3
    div $t0, $at
    mfhi $t3
    beq $t3, $zero, L_00400038
    addiu $at, $zero, 5
    div $t0, $at
    mfhi $t3
    beq $t3, $zero, L_0040005C
    addiu $v0, $zero, 1
    addu $a0, $t0, $zero
    syscall
    j L_00400080
L_00400038:
    addiu $at, $zero, 5
    div $t0, $at
    mfhi $t3
    beq $t3, $zero, L_00400070
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 0
    syscall
    j L_00400080
L_0040005C:
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 5
    syscall
    j L_00400080
L_00400070:
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 10
    syscall
L_00400080:
    addiu $v0, $zero, 11
    addiu $a0, $zero, 10
    syscall
    addi $t0, $t0, 1
    slt $at, $t1, $t0
    beq $at, $zero, L_00400008
    addiu $v0, $zero, 10
    syscall
