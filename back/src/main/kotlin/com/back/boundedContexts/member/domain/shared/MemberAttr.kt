package com.back.boundedContexts.member.domain.shared

import com.back.global.jpa.domain.BaseTime
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType.LAZY
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.SEQUENCE
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.NaturalId

@Entity
@DynamicUpdate
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["subject_id", "name"]),
    ],
)
class MemberAttr(
    @field:jakarta.persistence.Id
    @field:SequenceGenerator(name = "member_attr_seq_gen", sequenceName = "member_attr_seq", allocationSize = 50)
    @field:GeneratedValue(strategy = SEQUENCE, generator = "member_attr_seq")
    override val id: Int = 0,
    @field:NaturalId
    @field:ManyToOne(fetch = LAZY)
    @field:JoinColumn(name = "subject_id")
    val subject: Member,
    @field:NaturalId
    val name: String,
    @field:Column(name = "val", columnDefinition = "TEXT")
    var value: String,
) : BaseTime()
