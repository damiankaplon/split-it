package pl.damiankaplon.splitit.project

import jakarta.persistence.*
import pl.damiankaplon.splitit.policy.SplitPolicyType
import java.util.*

@Entity
@Table(name = "project")
class Project private constructor(
    @Column(name = "owner_id", nullable = false)
    val ownerId: String,

    @Column(name = "name", nullable = false)
    val name: String,

    /** Currency of every expense in the project; stored as its ISO 4217 code. */
    @Column(name = "currency", nullable = false)
    val currency: Currency,

    /** How every expense in the project is divided among its members. */
    @Enumerated(EnumType.STRING)
    @Column(name = "split_policy", nullable = false)
    val splitPolicy: SplitPolicyType,
) {
    @Id
    val id: UUID = UUID.randomUUID()

    constructor(
        name: String,
        owner: ProjectMember,
        currency: Currency,
        splitPolicy: SplitPolicyType = SplitPolicyType.EQUAL_SHARE,
    ) : this(
        owner.userId,
        name,
        currency,
        splitPolicy,
    ) {
        _members.add(owner)
    }


    @ElementCollection
    @CollectionTable(name = "project_member", joinColumns = [JoinColumn(name = "project_id")])
    private val _members: MutableSet<ProjectMember> = linkedSetOf()

    val members: Set<ProjectMember> get() = _members

    fun hasMember(userId: String): Boolean = _members.any { it.userId == userId }

    /**
     * Adds the member unless a member with the same user id already belongs to
     * the project.
     */
    fun add(member: ProjectMember) {
        require(!_members.contains(member)) { "$member is already a member of $this" }
        _members += member
    }
}
