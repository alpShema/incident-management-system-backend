package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.SlackNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlackNotificationPreferenceRepository extends JpaRepository<SlackNotificationPreference, UUID> {

    List<SlackNotificationPreference> findByUserId(String userId);

    Optional<SlackNotificationPreference> findByUserIdAndNotificationType(String userId, String notificationType);

    void deleteByUserId(String userId);
}
