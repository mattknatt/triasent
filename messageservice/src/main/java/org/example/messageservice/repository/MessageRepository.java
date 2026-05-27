package org.example.messageservice.repository;

import org.example.messageservice.model.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {
    List<MessageEntity> findAllByOrderByCreatedAtDesc();
    List<MessageEntity> findAllByUsernameOrderByCreatedAtDesc(String username);
    Optional<MessageEntity> findByIdempotencyKey(String idempotencyKey);
}
