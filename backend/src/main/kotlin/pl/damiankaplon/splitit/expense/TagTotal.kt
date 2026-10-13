package pl.damiankaplon.splitit.expense

/**
 * Sum of a tag's expenses in minor units; a null tag stands for the
 * untagged ones.
 */
data class TagTotal(val tag: String?, val tagColor: String?, val total: Long)
