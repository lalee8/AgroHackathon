package az.aqroinvest.application;

import az.aqroinvest.application.ApplicationDtos.TrackView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Status yoxlaması", description = "Girişsiz - müraciət nömrəsi və izləmə kodu ilə")
@RestController
@RequiredArgsConstructor
public class TrackController {
    private final ApplicationService service;

    @Operation(summary = "Müraciətin statusunu yoxla")
    @GetMapping("/api/v1/track")
    public TrackView track(@RequestParam String number, @RequestParam String code) {
        return service.track(number, code);
    }
}
