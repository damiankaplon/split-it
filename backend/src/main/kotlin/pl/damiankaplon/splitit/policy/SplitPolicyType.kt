package pl.damiankaplon.splitit.policy

/**
 * The [SplitPolicy] a project divides its expenses with; stored on the
 * project and accepted by the API by name.
 */
enum class SplitPolicyType(val policy: SplitPolicy) {
    EQUAL_SHARE(EqualShare()),
}
