package az.aqroinvest.security;

import az.aqroinvest.user.Role;
import jakarta.validation.constraints.*;

import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Ad və soyad tələb olunur") @Size(max = 160) String fullName,
            @NotBlank(message = "E-poçt tələb olunur") @Email(message = "E-poçt formatı yanlışdır") @Size(max = 160) String email,
            @NotBlank(message = "Şifrə tələb olunur")
            @Size(min = 8, max = 72, message = "Şifrə 8-72 simvol olmalıdır")
            @Pattern(regexp = "^(?=.*[A-Za-zƏəĞğİıÖöŞşÜüÇç])(?=.*\\d).+$", message = "Şifrədə ən azı bir hərf və bir rəqəm olmalıdır")
            String password,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{9,20}$", message = "Telefon nömrəsi yanlışdır") String phone,
            @Pattern(regexp = "^$|^[A-Za-z0-9]{7}$", message = "FİN kod 7 simvoldan ibarət olmalıdır") String fin,
            @AssertTrue(message = "Fərdi məlumatların emalına razılıq tələb olunur") boolean consent,
            /** INVESTOR, ENTREPRENEUR (sahibkar) və ya BUYER (alıcı). Əməkdaş rolları qeydiyyatla yaradıla bilməz. */
            String accountType,
            @jakarta.validation.Valid InvestorFields investor,
            @jakarta.validation.Valid EntrepreneurFields entrepreneur,
            @jakarta.validation.Valid LegalFields legal) {
    }

    /**
     * Hüquqi status və ona aid rekvizitlər. INDIVIDUAL: FİN; COMPANY: şirkətin adı, VÖEN, nümayəndənin vəzifəsi;
     * FOREIGN: vətəndaşlıq və pasport nömrəsi. Yerli sakin (rayonun keçmiş sakini) yalnız fiziki şəxs üçün.
     */
    public record LegalFields(String legalType,
                              @Pattern(regexp = "^$|^[0-9]{10}$", message = "VÖEN 10 rəqəmdən ibarət olmalıdır") String voen,
                              @Size(max = 200) String companyName,
                              @Size(max = 30) String companyForm,
                              @Size(max = 120) String repPosition,
                              @Size(max = 60) String citizenship,
                              @Pattern(regexp = "^$|^[A-Za-z0-9 ]{5,20}$", message = "Pasport nömrəsi 5-20 hərf və rəqəmdən ibarət olmalıdır") String passportNo,
                              Boolean localResident,
                              String homeDistrict,
                              @Pattern(regexp = "^$|^(az|en|ru)$", message = "Dil: az, en və ya ru") String prefLang) {
    }

    /** İnvestorun qeydiyyat sahələri: məbləğ aralığı, istiqamətlər, risk iştahı. */
    public record InvestorFields(@PositiveOrZero java.math.BigDecimal ticketMin, @PositiveOrZero java.math.BigDecimal ticketMax,
                                 java.util.List<String> sectors, String risk) {
    }

    /** Sahibkarın qeydiyyat sahələri: rayon, istiqamət, torpaq vəziyyəti, lazım olan investisiya, ideya. */
    public record EntrepreneurFields(String district, String sector, String landStatus,
                                     @PositiveOrZero @DecimalMax(value = "100000", message = "Sahə çox böyükdür") java.math.BigDecimal landHa,
                                     @PositiveOrZero java.math.BigDecimal needAmount,
                                     @Size(max = 400, message = "İdeya 400 simvoldan qısa olmalıdır") String idea) {
    }

    /** mygov ID ilə ilk girişdən sonra və ya profildə hesab növünün seçilməsi. */
    public record AccountTypeUpdate(@NotBlank String accountType, @jakarta.validation.Valid InvestorFields investor,
                                    @jakarta.validation.Valid EntrepreneurFields entrepreneur,
                                    @jakarta.validation.Valid LegalFields legal) {
    }

    public record LoginRequest(
            @NotBlank(message = "E-poçt tələb olunur") String email,
            @NotBlank(message = "Şifrə tələb olunur") String password) {
    }

    public record UserView(Long id, String email, String fullName, String phone, String fin, Role role,
                           String roleLabel, Instant createdAt, String userType, boolean mygov,
                           String legalType, String legalTypeLabel, String companyName, String companyForm, String voen,
                           String repPosition, String citizenship, String passportNo, boolean localResident,
                           String homeDistrict, String homeDistrictLabel, String prefLang, String displayName) {
    }

    public record AuthResponse(String token, Instant expiresAt, UserView user) {
    }

    public record ProfileUpdate(
            @NotBlank @Size(max = 160) String fullName,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{9,20}$", message = "Telefon nömrəsi yanlışdır") String phone,
            @Pattern(regexp = "^$|^[A-Za-z0-9]{7}$", message = "FİN kod 7 simvoldan ibarət olmalıdır") String fin) {
    }
}
