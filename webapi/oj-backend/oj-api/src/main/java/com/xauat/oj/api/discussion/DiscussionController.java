package com.xauat.oj.api.discussion;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.discussion.domain.Discussion;
import com.xauat.oj.core.discussion.domain.DiscussionLike;
import com.xauat.oj.core.discussion.domain.DiscussionReply;
import com.xauat.oj.core.discussion.domain.DiscussionReplyLike;
import com.xauat.oj.core.discussion.repository.*;
import com.xauat.oj.core.user.domain.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/discussions")
public class DiscussionController {
    private final CurrentUser currentUser; private final DiscussionRepository discussions; private final DiscussionReplyRepository replies; private final DiscussionLikeRepository likes; private final DiscussionReplyLikeRepository replyLikes;
    public DiscussionController(CurrentUser currentUser, DiscussionRepository discussions, DiscussionReplyRepository replies, DiscussionLikeRepository likes, DiscussionReplyLikeRepository replyLikes) { this.currentUser = currentUser; this.discussions = discussions; this.replies = replies; this.likes = likes; this.replyLikes = replyLikes; }

    @GetMapping({"", "/"})
    @Transactional
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestParam(defaultValue = "全部") String category,
                                          @RequestParam(defaultValue = "30") Integer limit,
                                          @RequestParam(defaultValue = "0") Integer offset) {
        List<Discussion> items = (category == null || category.isBlank() || "全部".equals(category.trim()))
                ? discussions.findAllByOrderByCreatedAtDescIdDesc()
                : discussions.findByCategoryOrderByCreatedAtDescIdDesc(category.trim());
        User me = currentUser.optional(authorization);
        int from = Math.max(0, offset); int to = Math.min(items.size(), from + Math.max(1, limit));
        return items.subList(from, to).stream().map(item -> view(item, true, me)).toList();
    }

    @PostMapping({"", "/"})
    public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody DiscussionRequest request) {
        Discussion item = discussions.save(Discussion.create(currentUser.require(authorization), request.title().trim(), request.content().trim(), request.category(), request.tags()));
        return view(item, false, null);
    }

    @GetMapping("/{id}")
    @Transactional
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        User me = currentUser.optional(authorization);
        return discussions.findById(id).map(item -> { item.view(); discussions.save(item); Map<String, Object> result = view(item, false, me); result.put("replies", replies.findByDiscussionIdOrderByCreatedAtAscIdAsc(id).stream().map(r -> replyView(r, me)).toList()); return ResponseEntity.ok(result); }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/replies")
    @Transactional
    public List<Map<String, Object>> replyList(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id,
                                               @RequestParam(defaultValue = "30") Integer limit, @RequestParam(defaultValue = "0") Integer offset) {
        List<DiscussionReply> items = replies.findByDiscussionIdOrderByCreatedAtAscIdAsc(id); User me = currentUser.optional(authorization);
        int from = Math.max(0, offset); int to = Math.min(items.size(), from + Math.max(1, limit));
        return items.subList(from, to).stream().map(r -> replyView(r, me)).toList();
    }

    @PostMapping("/{id}/like")
    @Transactional
    public ResponseEntity<?> like(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id, @RequestBody(required = false) LikeRequest request) {
        var user = currentUser.require(authorization); var item = discussions.findById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build();
        boolean was = likes.existsByDiscussionIdAndUserId(id, user.getId()); boolean want = request != null && Boolean.TRUE.equals(request.liked());
        if (want && !was) { likes.save(DiscussionLike.of(item, user)); item.likeAdded(); } else if (!want && was) { likes.deleteByDiscussionIdAndUserId(id, user.getId()); item.likeRemoved(); }
        discussions.save(item); return ResponseEntity.ok(Map.of("liked", want, "like_count", item.getLikeCount()));
    }

    @PostMapping("/{id}/replies")
    @Transactional
    public ResponseEntity<?> reply(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id, @Valid @RequestBody ReplyRequest request) {
        var discussion = discussions.findById(id).orElse(null); if (discussion == null) return ResponseEntity.notFound().build();
        User me = currentUser.require(authorization); discussion.replyAdded(); discussions.save(discussion);
        return ResponseEntity.status(201).body(replyView(replies.save(DiscussionReply.create(discussion, me, request.content().trim())), me));
    }

    @PostMapping("/replies/{id}/like")
    @Transactional
    public ResponseEntity<?> replyLike(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id, @RequestBody(required = false) LikeRequest request) {
        var user = currentUser.require(authorization); var reply = replies.findById(id).orElse(null); if (reply == null) return ResponseEntity.notFound().build();
        boolean was = replyLikes.existsByReplyIdAndUserId(id, user.getId()); boolean want = request != null && Boolean.TRUE.equals(request.liked());
        if (want && !was) { replyLikes.save(DiscussionReplyLike.of(reply, user)); reply.likeAdded(); } else if (!want && was) { replyLikes.deleteByReplyIdAndUserId(id, user.getId()); reply.likeRemoved(); }
        replies.save(reply); return ResponseEntity.ok(Map.of("liked", want, "like_count", reply.getLikeCount()));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = currentUser.require(authorization); var item = discussions.findById(id).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        if (!user.getUsername().equals(item.getAuthorName()) && !"manager".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        discussions.delete(item); return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/replies/{id}")
    @Transactional
    public ResponseEntity<?> deleteReply(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = currentUser.require(authorization); var reply = replies.findById(id).orElse(null); if (reply == null) return ResponseEntity.notFound().build();
        if (!user.getId().equals(reply.getAuthorId()) && !"manager".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        replies.delete(reply); return ResponseEntity.noContent().build();
    }

    private Map<String, Object> view(Discussion item, boolean truncate, User me) {
        String content = item.getContent() == null ? "" : item.getContent();
        if (truncate && content.length() > 200) content = content.substring(0, 200);
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", item.getId()); result.put("title", item.getTitle() == null ? "" : item.getTitle()); result.put("content", content);
        result.put("author_id", item.getAuthorId()); result.put("author_name", item.getAuthorName());
        result.put("category", item.getCategory() == null ? "全部" : item.getCategory()); result.put("tags", item.getTags() == null ? "" : item.getTags());
        result.put("reply_count", item.getReplyCount()); result.put("like_count", item.getLikeCount()); result.put("view_count", item.getViewCount());
        result.put("is_pinned", item.isPinned()); result.put("is_closed", item.isClosed());
        result.put("is_liked", me != null && likes.existsByDiscussionIdAndUserId(item.getId(), me.getId()));
        result.put("created_at", item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
        return result;
    }
    private Map<String, Object> replyView(DiscussionReply item, User me) {
        return Map.of("id", item.getId(), "content", item.getContent() == null ? "" : item.getContent(), "author_id", item.getAuthorId(),
                "author_name", item.getAuthorName(), "like_count", item.getLikeCount(), "is_liked", me != null && replyLikes.existsByReplyIdAndUserId(item.getId(), me.getId()),
                "created_at", item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
    }
    public record DiscussionRequest(@NotBlank @Size(max = 200) String title, @NotBlank String content, String category, String tags) {}
    public record ReplyRequest(@NotBlank String content) {}
    public record LikeRequest(@JsonProperty("liked") Boolean liked) {}
}
