package dev.zihowl.dog.util

object PasswordPolicy {

    sealed class Result {
        object Ok : Result()
        object TooShort : Result()
        object NotEnoughCategories : Result()
    }

    fun validate(password: String): Result {
        if (password.length < 8) return Result.TooShort
        var categories = 0
        if (password.any { it.isUpperCase() }) categories++
        if (password.any { it.isLowerCase() }) categories++
        if (password.any { it.isDigit() }) categories++
        if (password.any { !it.isLetterOrDigit() }) categories++
        return if (categories >= 3) Result.Ok else Result.NotEnoughCategories
    }
}
