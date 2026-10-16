package az.aqroinvest.commission;

import az.aqroinvest.application.Application;
import az.aqroinvest.application.ApplicationDtos.ApplicationListItem;
import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.application.ApplicationStatus;
import az.aqroinvest.application.Decision;
import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.competition.Competition;
import az.aqroinvest.competition.CompetitionRepository;
import az.aqroinvest.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Müsabiqə komissiyası. Qəbul edən tərəf dövlətdir: dövlət qurumlarının nümayəndələri hər müraciət üzrə səs verir.
 * Qaydalar (3.5): kvorum üzvlərin ən azı üçdə ikisidir; qərar iclasda iştirak edən üzvlərin sadə səs çoxluğu ilə açıq
 * səsvermə yolu ilə qəbul edilir; bitərəf qalmağa yol verilmir; səslər bərabər olduqda sədrin səsi həlledicidir.
 * Səsvermə aparılıbsa, katibin rəsmiləşdirdiyi qərar onun nəticəsinə zidd ola bilməz.
 */
@Service
@RequiredArgsConstructor
public class CommissionService {
    private final CommissionMemberRepository members;
    private final CommissionVoteRepository votes;
    private final ApplicationRepository applications;
    private final CompetitionRepository competitions;
    private final AuditService audit;

    public record MemberView(Long id, String fullName, String position, String institution, String institutionLabel,
                             String institutionAbbr, String role, String roleLabel, boolean votes, boolean hasAccount) {}

    public record VoteView(Long memberId, String fullName, String institutionLabel, String institutionAbbr, String roleLabel,
                           String vote, String voteLabel, String comment, Instant at) {}

    public record Tally(int votingMembers, int forVotes, int against, int pending, int quorum, int majority,
                        boolean quorumReached, String chairVote, String outcome, String outcomeLabel) {}

    public record VotesView(Long applicationId, List<VoteView> votes, Tally tally, Long myMemberId, String myVote, boolean canVote) {}

    public record ProtocolRow(int rank, Long applicationId, String appNumber, String applicant, String applicantType, String plotCode,
                              String plotName, Integer finalScore, Integer autoScore, Integer expertAverage, Tally tally,
                              String decision, String decisionLabel, String statusLabel) {}

    public record Protocol(Long competitionId, String competitionCode, String competitionTitle, Instant generatedAt,
                           List<MemberView> members, List<ProtocolRow> rows, String rule) {}

    @Transactional(readOnly = true)
    public List<MemberView> members() {
        return members.findAllByOrderBySortOrderAsc().stream().map(CommissionService::view).toList();
    }

    /** Hamıya açıq: komissiyada hansı dövlət qurumları təmsil olunur (adlar və vəzifələr olmadan). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> institutions() {
        Map<Institution, List<CommissionMember>> by = members.findAllByOrderBySortOrderAsc().stream()
                .collect(Collectors.groupingBy(CommissionMember::getInstitution, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        by.forEach((inst, list) -> out.add(Map.of("institution", inst.name(), "label", inst.label(), "abbr", inst.abbr(),
                "chair", list.stream().anyMatch(m -> m.getMemberRole() == CommissionMember.Role.CHAIR),
                "seats", (int) list.stream().filter(m -> m.getMemberRole().votes()).count())));
        return out;
    }

    @Transactional(readOnly = true)
    public VotesView votes(Long applicationId, AuthPrincipal me) {
        Application a = applications.findById(applicationId).orElseThrow(() -> ApiException.notFound("Müraciət"));
        List<CommissionMember> all = members.findAllByOrderBySortOrderAsc();
        Map<Long, CommissionVote> byMember = votes.findByApplicationId(applicationId).stream()
                .collect(Collectors.toMap(CommissionVote::getMemberId, Function.identity()));
        List<VoteView> list = all.stream().filter(m -> m.getMemberRole().votes()).map(m -> {
            CommissionVote v = byMember.get(m.getId());
            return new VoteView(m.getId(), m.getFullName(), m.getInstitution().label(), m.getInstitution().abbr(), m.getMemberRole().label(),
                    v == null ? null : v.getVote().name(), v == null ? "Səs verməyib" : v.getVote().label(), v == null ? null : v.getComment(),
                    v == null ? null : v.getCreatedAt());
        }).toList();
        CommissionMember mine = me == null ? null : members.findFirstByUserId(me.id()).orElse(null);
        boolean open = votingOpen(a);
        String myVote = mine == null || byMember.get(mine.getId()) == null ? null : byMember.get(mine.getId()).getVote().name();
        return new VotesView(applicationId, list, tally(all, byMember.values()), mine == null ? null : mine.getId(), myVote,
                mine != null && mine.getMemberRole().votes() && open);
    }

    @Transactional
    public VotesView vote(Long applicationId, AuthPrincipal me, String voteValue, String comment) {
        Application a = applications.findById(applicationId).orElseThrow(() -> ApiException.notFound("Müraciət"));
        CommissionMember m = members.findFirstByUserId(me.id())
                .orElseThrow(() -> ApiException.forbidden("Siz komissiyanın üzvü deyilsiniz"));
        if (!m.getMemberRole().votes()) throw ApiException.forbidden("Komissiya katibi səs vermir, qərarı rəsmiləşdirir");
        if (!votingOpen(a)) throw ApiException.conflict("VOTING_CLOSED", "Bu müraciət üzrə səsvermə bağlıdır (" + a.getStatus().label() + ")");
        CommissionVote.Vote v;
        try {
            v = CommissionVote.Vote.valueOf(voteValue == null ? "" : voteValue);
        } catch (IllegalArgumentException e) {
            throw ApiException.fields("Səs seçilməyib", Map.of("vote", "Lehinə və ya əleyhinə seçin: Qaydalara görə bitərəf qalmağa yol verilmir"));
        }
        String c = comment == null || comment.isBlank() ? null : comment.trim();
        if (v == CommissionVote.Vote.AGAINST && c == null) {
            throw ApiException.fields("Əleyhinə səsin səbəbi yazılmalıdır", Map.of("comment", "Əleyhinə səs əsaslandırılmalıdır"));
        }
        CommissionVote cv = votes.findByApplicationIdAndMemberId(applicationId, m.getId()).orElseGet(CommissionVote::new);
        cv.setApplicationId(applicationId);
        cv.setMemberId(m.getId());
        cv.setVote(v);
        cv.setComment(c == null ? null : c.length() > 600 ? c.substring(0, 600) : c);
        cv.setCreatedAt(Instant.now());
        votes.save(cv);
        audit.log("COMMISSION_VOTE", "Application", applicationId, m.getInstitution().abbr() + " " + v.name());
        return votes(applicationId, me);
    }

    /**
     * Qərar səsvermə ilə uyğun olmalıdır: səsvermə aparılıbsa, kvorum olmadan qərar verilmir, qalib elan etmək üçün
     * səs çoxluğu (bərabərlikdə sədrin səsi) lazımdır. Səsvermə heç aparılmayıbsa (köhnə müraciətlər), qayda tətbiq olunmur.
     */
    @Transactional(readOnly = true)
    public void checkDecision(Application a, Decision decision) {
        Collection<CommissionVote> vs = votes.findByApplicationId(a.getId());
        if (vs.isEmpty() || decision == null) return;
        Tally t = tally(members.findAllByOrderBySortOrderAsc(), vs);
        if (!t.quorumReached()) {
            throw ApiException.conflict("NO_QUORUM", "Komissiyada kvorum yoxdur: " + (t.forVotes() + t.against()) + " səs, ən azı " + t.quorum() + " lazımdır");
        }
        if (t.outcome().equals("TIE")) {
            throw ApiException.conflict("TIE_NO_CHAIR", "Səslər bərabərdir: sədrin səsi həlledicidir, sədr hələ səs verməyib");
        }
        if (decision == Decision.APPROVED && !"APPROVED".equals(t.outcome())) {
            throw ApiException.conflict("NO_MAJORITY", "Qalib elan etmək üçün komissiyanın səs çoxluğu yoxdur: lehinə " + t.forVotes() + ", əleyhinə " + t.against());
        }
        if (decision == Decision.REJECTED && "APPROVED".equals(t.outcome())) {
            throw ApiException.conflict("MAJORITY_FOR", "Komissiya səs çoxluğu ilə lehinədir: imtina qərarı səsvermənin nəticəsinə ziddir");
        }
    }

    /** Protokol: müraciətlər yekun bala görə sıralanır (ApplicationService.ranking), hər birinin səsvermə nəticəsi əlavə olunur. */
    @Transactional(readOnly = true)
    public Protocol protocol(Long competitionId, List<ApplicationListItem> ranking) {
        Competition c = competitions.findById(competitionId).orElseThrow(() -> ApiException.notFound("Müsabiqə"));
        List<CommissionMember> all = members.findAllByOrderBySortOrderAsc();
        Map<Long, List<CommissionVote>> byApp = votes.findByApplicationIdIn(ranking.stream().map(ApplicationListItem::id).toList()).stream()
                .collect(Collectors.groupingBy(CommissionVote::getApplicationId));
        Map<Long, Application> apps = applications.findAllById(ranking.stream().map(ApplicationListItem::id).toList()).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        List<ProtocolRow> rows = new ArrayList<>();
        int rank = 0;
        for (ApplicationListItem i : ranking) {
            Application a = apps.get(i.id());
            rows.add(new ProtocolRow(++rank, i.id(), i.appNumber(), i.applicantName(),
                    a == null || a.getApplicantType() == null ? null : a.getApplicantType().label(), i.plotCode(), i.plotName(),
                    i.finalScore(), i.autoScore(), i.expertAverage(), tally(all, byApp.getOrDefault(i.id(), List.of())),
                    i.decision() == null ? null : i.decision().name(), i.decisionLabel(), i.statusLabel()));
        }
        return new Protocol(c.getId(), c.getCode(), c.getTitle(), Instant.now(), all.stream().map(CommissionService::view).toList(), rows,
                RULE);
    }

    static final String RULE = "Qaydalar, 3.5: kvorum üzvlərin ən azı 2/3-si; qərar iştirak edən üzvlərin sadə səs çoxluğu ilə açıq səsvermə yolu ilə qəbul edilir; "
            + "bitərəf qalmağa yol verilmir; səslər bərabər olduqda sədrin səsi həlledicidir. Bərabər balda yerli müraciətçiyə üstünlük verilir (4.10.7).";

    static Tally tally(List<CommissionMember> all, Collection<CommissionVote> vs) {
        Set<Long> voting = all.stream().filter(m -> m.getMemberRole().votes()).map(CommissionMember::getId).collect(Collectors.toSet());
        Long chairId = all.stream().filter(m -> m.getMemberRole() == CommissionMember.Role.CHAIR).map(CommissionMember::getId).findFirst().orElse(null);
        int n = voting.size();
        int f = 0, ag = 0;
        String chair = null;
        for (CommissionVote v : vs) {
            if (!voting.contains(v.getMemberId())) continue;
            if (v.getVote() == CommissionVote.Vote.FOR) f++;
            else ag++;
            if (v.getMemberId().equals(chairId)) chair = v.getVote().name();
        }
        int cast = f + ag;
        int quorum = (int) Math.ceil(n * 2 / 3.0);
        boolean q = cast >= quorum;
        String outcome;
        if (cast == 0) outcome = "NONE";
        else if (!q) outcome = "NO_QUORUM";
        else if (f > ag) outcome = "APPROVED";
        else if (f < ag) outcome = "REJECTED";
        else outcome = chair == null ? "TIE" : chair.equals("FOR") ? "APPROVED" : "REJECTED";
        boolean tieBroken = q && f == ag && chair != null;
        String label = switch (outcome) {
            case "NONE" -> "Səsvermə başlamayıb";
            case "NO_QUORUM" -> "Kvorum yoxdur";
            case "APPROVED" -> tieBroken ? "Bərabərlik, sədrin səsi ilə: qalib" : "Səs çoxluğu: qalib elan oluna bilər";
            case "REJECTED" -> tieBroken ? "Bərabərlik, sədrin səsi ilə: imtina" : "Səs çoxluğu əleyhinə: imtina";
            default -> "Səslər bərabərdir: sədrin səsi gözlənilir";
        };
        return new Tally(n, f, ag, n - cast, quorum, cast / 2 + 1, q, chair, outcome, label);
    }

    private static boolean votingOpen(Application a) {
        return a.getStatus() == ApplicationStatus.SUBMITTED || a.getStatus() == ApplicationStatus.UNDER_REVIEW
                || a.getStatus() == ApplicationStatus.INFO_REQUESTED;
    }

    private static MemberView view(CommissionMember m) {
        return new MemberView(m.getId(), m.getFullName(), m.getPosition(), m.getInstitution().name(), m.getInstitution().label(),
                m.getInstitution().abbr(), m.getMemberRole().name(), m.getMemberRole().label(), m.getMemberRole().votes(), m.getUserId() != null);
    }
}
