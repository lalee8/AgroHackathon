package az.aqroinvest.machinery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findByActiveTrueOrderByCreatedAtDesc();

    List<Equipment> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
