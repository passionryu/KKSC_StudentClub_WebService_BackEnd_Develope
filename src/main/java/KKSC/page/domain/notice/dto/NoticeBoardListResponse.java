package KKSC.page.domain.notice.dto;

import java.time.LocalDateTime;

public record NoticeBoardListResponse (
        String title,
        String createdBy,
        Long fileYN,
        Long view,
        Long delYN,
        LocalDateTime createdAt
) {}