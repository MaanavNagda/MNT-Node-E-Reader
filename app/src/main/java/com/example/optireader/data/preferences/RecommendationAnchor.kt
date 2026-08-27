package com.example.optireader.data.preferences

enum class RecommendationAnchor {
    SameAuthor,
    SameGenre,
    DifferentAuthor,
    DifferentGenre,
    ;

    companion object {
        fun fromStorage(s: String?): RecommendationAnchor =
            when (s) {
                "same_genre" -> SameGenre
                "different_author" -> DifferentAuthor
                "different_genre" -> DifferentGenre
                else -> SameAuthor
            }

        fun toStorage(v: RecommendationAnchor): String =
            when (v) {
                SameAuthor -> "same_author"
                SameGenre -> "same_genre"
                DifferentAuthor -> "different_author"
                DifferentGenre -> "different_genre"
            }
    }
}
