.data
fizz:     .asciiz "Fizz"
buzz:     .asciiz "Buzz"
fizzbuzz: .asciiz "FizzBuzz"

.text
.globl main

main:
    li $t0, 1
    li $t1, 100

loop:
    rem $t3, $t0, 3
    beq $t3, $zero, check_five

    rem $t3, $t0, 5
    beq $t3, $zero, print_buzz

    li $v0, 1
    move $a0, $t0
    syscall
    j print_newline

check_five:
    rem $t3, $t0, 5
    beq $t3, $zero, print_fizzbuzz

    li $v0, 4
    la $a0, fizz
    syscall
    j print_newline

print_buzz:
    li $v0, 4
    la $a0, buzz
    syscall
    j print_newline

print_fizzbuzz:
    li $v0, 4
    la $a0, fizzbuzz
    syscall

print_newline:
    li $v0, 11
    li $a0, 10
    syscall

    addi $t0, $t0, 1
    ble $t0, $t1, loop

    li $v0, 10
    syscall