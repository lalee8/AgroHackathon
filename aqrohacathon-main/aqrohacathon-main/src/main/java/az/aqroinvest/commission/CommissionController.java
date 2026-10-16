package az.aqroinvest.commission;

import az.aqroinvest.application.ApplicationService;
import az.aqroinvest.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Müsabiqə komissiyası", description = "Dövlət qurumlarının nümayəndələrindən ibarət komissiya: tərkib, səsvermə, protokol")
@RestController
@RequiredArgsConstructor
public class CommissionController {
    private final CommissionService commission;
    private final ApplicationService applications;
    private final CurrentUser currentUser;

    public record VoteRequest(String vote, String comment) {}

    @Operation(summary = "Komissiyada təmsil olunan dövlət qurumları (hamıya açıq)")
    @GetMapping("/api/v1/competitions/commission")
    public List<Map<String, Object>> institutions() {
        return commission.institutions();
    }

    @Operation(summary = "Komissiyanın tərkibi (əməkdaşlar üçün)")
    @GetMapping("/api/v1/review/commission")
    public List<CommissionService.MemberView> members() {
        return commission.members();
    }

    @Operation(summary = "Müraciət üzrə komissiya üzvlərinin səsləri və nəticə")
    @GetMapping("/api/v1/review/applications/{id}/votes")
    public CommissionService.VotesView votes(@PathVariable Long id) {
        return commission.votes(id, currentUser.require());
    }

    @Operation(summary = "Komissiya üzvünün səsi: FOR (lehinə) və ya AGAINST (əleyhinə, səbəb məcburidir). Qaydalar 3.5: bitərəf qalmaq olmur")
    @PostMapping("/api/v1/review/applications/{id}/votes")
    public CommissionService.VotesView vote(@PathVariable Long id, @RequestBody VoteRequest req) {
        return commission.vote(id, currentUser.require(), req.vote(), req.comment());
    }

    @Operation(summary = "Komissiya iclasının protokolu: müraciətlər yekun bala görə, səsvermə və qərar")
    @GetMapping("/api/v1/review/competitions/{id}/protocol")
    public CommissionService.Protocol protocol(@PathVariable Long id) {
        return commission.protocol(id, applications.ranking(id));
    }
}
