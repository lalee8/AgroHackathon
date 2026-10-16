package az.aqroinvest.commission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Komissiya üzvü: dövlət qurumunun nümayəndəsi. Portal hesabı varsa (userId) səsini özü verir. */
@Entity
@Table(name = "commission_members")
@Getter
@Setter
@NoArgsConstructor
public class CommissionMember {
    /** Sədr və üzvlər səs verir (bərabərlikdə sədrin səsi həlledicidir); katib Nazirliyin əməkdaşıdır, iclası rəsmiləşdirir, səs vermir. */
    public enum Role {
        CHAIR("Komissiyanın sədri", true), MEMBER("Komissiya üzvü", true), SECRETARY("Katib (səs hüququ yoxdur)", false);

        private final String label;
        private final boolean votes;

        Role(String label, boolean votes) {
            this.label = label;
            this.votes = votes;
        }

        public String label() { return label; }

        public boolean votes() { return votes; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 200)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 20)
    private Role memberRole;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
