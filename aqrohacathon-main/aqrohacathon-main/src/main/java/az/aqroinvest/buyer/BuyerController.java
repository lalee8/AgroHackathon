package az.aqroinvest.buyer;

import az.aqroinvest.buyer.BuyerDtos.*;
import az.aqroinvest.plot.PlotCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Alıcılar və niyyət məktubları", description = "Təsdiqlənmiş alıcı tələbləri, sahə ilə uyğunlaşdırma, niyyət məktubu")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BuyerController {
    private final BuyerService service;

    @Operation(summary = "Təsdiqlənmiş alıcı tələbləri")
    @GetMapping("/demands")
    public List<DemandView> list(@RequestParam(required = false) PlotCategory category) {
        return service.verified(category);
    }

    @Operation(summary = "Tələb")
    @GetMapping("/demands/{id}")
    public DemandView one(@PathVariable Long id) {
        return service.one(id);
    }

    @Operation(summary = "Sahənin məhsuluna uyğun alıcılar (məhsul, məsafə, həcm)")
    @GetMapping("/plots/{id}/demands")
    public List<DemandMatch> forPlot(@PathVariable Long id) {
        return service.forPlot(id);
    }

    @Operation(summary = "Alıcıdan niyyət məktubu istə (investor / sahibkar)")
    @PostMapping("/demands/{id}/letters")
    public LetterView request(@PathVariable Long id, @Valid @RequestBody LetterRequest r) {
        return service.requestLetter(id, r);
    }

    @Operation(summary = "Niyyət məktublarım (və ya müraciətə / layihəyə əlavə olunanlar)")
    @GetMapping("/letters")
    public List<LetterView> letters(@RequestParam(required = false) Long applicationId, @RequestParam(required = false) Long projectId) {
        return service.myLetters(applicationId, projectId);
    }

    @Operation(summary = "Alıcı: mənim tələblərim")
    @GetMapping("/buyer/demands")
    public List<DemandView> mine() {
        return service.mine();
    }

    @Operation(summary = "Alıcı: yeni tələb (Nazirliyin yoxlamasına göndərilir)")
    @PostMapping("/buyer/demands")
    public DemandView create(@Valid @RequestBody DemandRequest r) {
        return service.create(r);
    }

    @Operation(summary = "Alıcı: tələbi redaktə et (yenidən yoxlamaya gedir)")
    @PutMapping("/buyer/demands/{id}")
    public DemandView update(@PathVariable Long id, @Valid @RequestBody DemandRequest r) {
        return service.update(id, r);
    }

    @Operation(summary = "Alıcı: gələn niyyət məktubu sorğuları")
    @GetMapping("/buyer/letters")
    public List<LetterView> incoming() {
        return service.incoming();
    }

    @Operation(summary = "Alıcı: niyyət məktubunu imzala")
    @PostMapping("/buyer/letters/{id}/sign")
    public LetterView sign(@PathVariable Long id, @RequestBody(required = false) SignRequest r) {
        return service.decide(id, true, r);
    }

    @Operation(summary = "Alıcı: niyyət məktubundan imtina")
    @PostMapping("/buyer/letters/{id}/decline")
    public LetterView decline(@PathVariable Long id, @RequestBody(required = false) SignRequest r) {
        return service.decide(id, false, r);
    }

    @Operation(summary = "Admin: bütün tələblər")
    @GetMapping("/admin/demands")
    public List<DemandView> all() {
        return service.all();
    }

    @Operation(summary = "Admin: tələbi təsdiqlə və ya rədd et")
    @PostMapping("/admin/demands/{id}/verify")
    public DemandView verify(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean ok) {
        return service.verify(id, ok);
    }
}
