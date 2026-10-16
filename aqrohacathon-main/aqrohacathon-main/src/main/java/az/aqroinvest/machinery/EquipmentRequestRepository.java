package az.aqroinvest.machinery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRequestRepository extends JpaRepository<EquipmentRequest, Long> {
    List<EquipmentRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<EquipmentRequest> findByEquipmentOwnerIdOrderByCreatedAtDesc(Long ownerId);

    long countByEquipmentIdAndStatus(Long equipmentId, EquipmentRequest.Status status);
}
