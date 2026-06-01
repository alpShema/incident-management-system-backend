package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.MessageMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MessageMediaRepository extends JpaRepository<MessageMedia, String> {
    List<MessageMedia> findByMessageId(String messageId);
    List<MessageMedia> findByMessageIdIn(Collection<String> messageIds);
}
