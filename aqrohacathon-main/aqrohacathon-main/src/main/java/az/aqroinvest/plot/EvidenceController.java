package az.aqroinvest.plot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sahələr", description = "Sahə haqqında məlumatın mənbəyi və tamlığı")
@RestController
@RequiredArgsConstructor
public class EvidenceController {
    private final EvidenceService evidence;

    @Operation(summary = "AgroEvidence: hər faktın mənbəyi, real və ya demo olması, tarixi və çatışmayan məlumatlar")
    @GetMapping("/api/v1/plots/{id}/evidence")
    public EvidenceService.Evidence evidence(@PathVariable Long id) {
        return evidence.evidence(id);
    }
}
