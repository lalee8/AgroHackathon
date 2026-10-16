package az.aqroinvest.plan;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Fəaliyyət planı", description = "Bu torpaqda nə, nə vaxt, niyə və nə ilə: iqlim, təqvim, su balansı, resurslar, risklər, ilk 12 ay")
@RestController
@RequiredArgsConstructor
public class PlanController {
    private final PlanService plans;

    @Operation(summary = "Sahə üçün fəaliyyət planı (use: istiqamət, item: bitki və ya fəaliyyət; boş olsa ən uyğunu seçilir)")
    @GetMapping("/api/v1/plots/{id}/plan")
    public PlanService.Plan plan(@PathVariable Long id, @RequestParam(required = false) String use, @RequestParam(required = false) String item) {
        return plans.plan(id, use, item);
    }
}
