package com.aaa.orchestrator.engine

import java.security.SecureRandom

/**
 * Generates realistic human profile metadata (name, birth date, username)
 * for automated account registration.
 */
object AccountProfileGenerator {

    private val FIRST_NAMES = listOf(
        "James", "Alexander", "Daniel", "Oliver", "Lucas", "Benjamin", "Henry", "Sebastian",
        "Liam", "Noah", "William", "Jack", "Theodore", "Leo", "Julian", "Gabriel",
        "Emma", "Sophia", "Olivia", "Ava", "Mia", "Amelia", "Harper", "Evelyn",
        "Charlotte", "Abigail", "Emily", "Elizabeth", "Ella", "Scarlett", "Grace", "Chloe"
    )

    private val LAST_NAMES = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
        "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White",
        "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker", "Young"
    )

    private val random = SecureRandom()

    fun generateFullName(): String {
        val first = FIRST_NAMES[random.nextInt(FIRST_NAMES.size)]
        val last = LAST_NAMES[random.nextInt(LAST_NAMES.size)]
        return "$first $last"
    }

    data class BirthDate(val month: Int, val day: Int, val year: Int)

    fun generateBirthDate(): BirthDate {
        val month = random.nextInt(12) + 1 // 1 - 12
        val day = random.nextInt(28) + 1   // 1 - 28
        val year = 1993 + random.nextInt(9) // 1993 - 2001 (adult age 23-31)
        return BirthDate(month, day, year)
    }
}
