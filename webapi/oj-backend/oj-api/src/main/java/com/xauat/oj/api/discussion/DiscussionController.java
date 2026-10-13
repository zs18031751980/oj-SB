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

/**
 * 讨论区接口（{@code /discussions}）：帖子、回复、点赞与删除。
 *
 * <p>列表内容可匿名浏览（仅返回截断正文）；发帖/点赞/删除需要登录。
 * 删除权限归属作者本人或 manager。</p>
 */
@RestController
@RequestMapping("/discussions")
public class DiscussionController {
    private final CurrentUser currentUser; private final DiscussionRepository discussions; private final DiscussionReplyRepository replies; private final DiscussionLikeRepository likes; private final DiscussionReplyLikeRepository replyLikes;
    public DiscussionController(CurrentUser currentUser, DiscussionRepository discussions, DiscussionReplyRepository replies, DiscussionLikeRepository likes, DiscussionReplyLikeRepository replyLikes) { this.currentUser = currentUser; this.discussions = discussions; this.replies = replies; this.likes = likes; this.replyLikes = replyLikes; }

    /**
     * 分页列出讨论帖。
     *
     * @param authorization 认证头（可空，用于回填 is_liked）
     * @param category      分类，缺省或“全部”表示不过滤
     * @param limit         每页条数
     * @param offset        偏移量
     * @return 帖子列表（正文截断至 200 字）
     */
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
        // 内存分页：夹取到合理区间，limit 至少为 1。
        int from = Math.max(0, offset); int to = Math.min(items.size(), from + Math.max(1, limit));
        return items.subList(from, to).stream().map(item -> view(item, true, me)).toList();
    }

    /**
     * 发表讨论帖。
     *
     * @param authorization 认证头
     * @param request       标题/正文/分类/标签
     * @return 201 与帖子视图
     */
    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> create(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody DiscussionRequest request) {
        Discussion item = discussions.save(Discussion.create(currentUser.require(authorization), request.title().trim(), request.content().trim(), request.category(), request.tags()));
        return ResponseEntity.status(201).body(view(item, false, null));
    }

    /**
     * 查看帖子详情（含回复列表），并自增浏览数。
     *
     * @param authorization 认证头（可空）
     * @param id            帖子 id
     * @return 帖子详情与回复；不存在返回 404
     */
    @GetMapping("/{id}")
    @Transactional
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        User me = currentUser.optional(authorization);
        return discussions.findById(id).map(item -> { item.view(); discussions.save(item); Map<String, Object> result = view(item, false, me); result.put("replies", replies.findByDiscussion_IdOrderByCreatedAtAscIdAsc(id).stream().map(r -> replyView(r, me)).toList()); return ResponseEntity.ok(result); }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 分页列出某帖的回复。
     *
     * @param authorization 认证头（可空）
     * @param id            帖子 id
     * @param limit         每页条数
     * @param offset        偏移量
     * @return 回复列表
     */
    @GetMapping("/{id}/replies")
    @Transactional
    public List<Map<String, Object>> replyList(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id,
                                               @RequestParam(defaultValue = "30") Integer limit, @RequestParam(defaultValue = "0") Integer offset) {
        List<DiscussionReply> items = replies.findByDiscussion_IdOrderByCreatedAtAscIdAsc(id); User me = currentUser.optional(authorization);
        int from = Math.max(0, offset); int to = Math.min(items.size(), from + Math.max(1, limit));
        return items.subList(from, to).stream().map(r -> replyView(r, me)).toList();
    }

    /**
     * 点赞/取消点赞帖子（幂等）。
     *
     * <p>根据 {@code liked} 与当前状态差异增减点赞记录，并同步帖子的 like_count。</p>
     *
     * @param authorization 认证头
     * @param id            帖子 id
     * @param request       目标点赞状态
     * @return 当前点赞状态与计数；帖子不存在返回 404
     */
    @PostMapping("/{id}/like")
    @Transactional
    public ResponseEntity<?> like(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id, @RequestBody(required = false) LikeRequest request) {
        var user = currentUser.require(authorization); var item = discussions.findById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build();
        boolean was = likes.existsByDiscussion_IdAndUser_Id(id, user.getId()); boolean want = request != null && Boolean.TRUE.equals(request.liked());
        // 仅在状态发生变化时写库，避免重复点赞。
        if (want && !was) { likes.save(DiscussionLike.of(item, user)); item.likeAdded(); } else if (!want && was) { likes.deleteByDiscussion_IdAndUser_Id(id, user.getId()); item.likeRemoved(); }
        discussions.save(item); return ResponseEntity.ok(Map.of("liked", want, "like_count", item.getLikeCount()));
    }

    /**
     * 回复帖子，并同步帖子的回复数。
     *
     * @param authorization 认证头
     * @param id            帖子 id
     * @param request       回复内容
     * @return 201 与回复视图；帖子不存在返回 404
     */
    @PostMapping("/{id}/replies")
    @Transactional
    public ResponseEntity<?> reply(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id, @Valid @RequestBody ReplyRequest request) {
        var discussion = discussions.findById(id).orElse(null); if (discussion == null) return ResponseEntity.notFound().build();
        User me = currentUser.require(authorization); discussion.replyAdded(); discussions.save(discussion);
        return ResponseEntity.status(201).body(replyView(replies.save(DiscussionReply.create(discussion, me, request.content().trim())), me));
    }

    /**
     * 点赞/取消点赞回复（幂等）。
     *
     * @param authorization 认证头
     * @param id            回复 id
     * @param request       目标点赞状态
     * @return 当前点赞状态与计数；回复不存在返回 404
     */
    @PostMapping("/replies/{id}/like")
    @Transactional
    public ResponseEntity<?> replyLike(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id, @RequestBody(required = false) LikeRequest request) {
        var user = currentUser.require(authorization); var reply = replies.findById(id).orElse(null); if (reply == null) return ResponseEntity.notFound().build();
        boolean was = replyLikes.existsByReply_IdAndUser_Id(id, user.getId()); boolean want = request != null && Boolean.TRUE.equals(request.liked());
        if (want && !was) { replyLikes.save(DiscussionReplyLike.of(reply, user)); reply.likeAdded(); } else if (!want && was) { replyLikes.deleteByReply_IdAndUser_Id(id, user.getId()); reply.likeRemoved(); }
        replies.save(reply); return ResponseEntity.ok(Map.of("liked", want, "like_count", reply.getLikeCount()));
    }

    /**
     * 删除帖子（敏感操作）。
     *
     * <p>权限：作者本人或 manager。</p>
     *
     * @param authorization 认证头
     * @param id            帖子 id
     * @return 204；不存在返回 404，无权返回 403
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = currentUser.require(authorization); var item = discussions.findById(id).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        if (!user.getUsername().equals(item.getAuthorName()) && !"manager".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        discussions.delete(item); return ResponseEntity.noContent().build();
    }

    /**
     * 删除回复（敏感操作）。
     *
     * <p>权限：回复作者本人或 manager。</p>
     *
     * @param authorization 认证头
     * @param id            回复 id
     * @return 204；不存在返回 404，无权返回 403
     */
    @DeleteMapping("/replies/{id}")
    @Transactional
    public ResponseEntity<?> deleteReply(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = currentUser.require(authorization); var reply = replies.findById(id).orElse(null); if (reply == null) return ResponseEntity.notFound().build();
        if (!user.getId().equals(reply.getAuthorId()) && !"manager".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        replies.delete(reply); return ResponseEntity.noContent().build();
    }

    /** 构造帖子对外视图；列表场景对正文截断，并回填当前用户是否已点赞。 */
    private Map<String, Object> view(Discussion item, boolean truncate, User me) {
        String content = item.getContent() == null ? "" : item.getContent();
        if (truncate && content.length() > 200) content = content.substring(0, 200);
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", item.getId()); result.put("title", item.getTitle() == null ? "" : item.getTitle()); result.put("content", content);
        result.put("author_id", item.getAuthorId()); result.put("author_name", item.getAuthorName());
        result.put("category", item.getCategory() == null ? "全部" : item.getCategory()); result.put("tags", item.getTags() == null ? "" : item.getTags());
        result.put("reply_count", item.getReplyCount()); result.put("like_count", item.getLikeCount()); result.put("view_count", item.getViewCount());
        result.put("is_pinned", item.isPinned()); result.put("is_closed", item.isClosed());
        result.put("is_liked", me != null && likes.existsByDiscussion_IdAndUser_Id(item.getId(), me.getId()));
        result.put("created_at", item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
        return result;
    }
    private Map<String, Object> replyView(DiscussionReply item, User me) {
        return Map.of("id", item.getId(), "content", item.getContent() == null ? "" : item.getContent(), "author_id", item.getAuthorId(),
                "author_name", item.getAuthorName(), "like_count", item.getLikeCount(), "is_liked", me != null && replyLikes.existsByReply_IdAndUser_Id(item.getId(), me.getId()),
                "created_at", item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
    }
    public record DiscussionRequest(@NotBlank @Size(max = 200) String title, @NotBlank String content, String category, String tags) {}
    public record ReplyRequest(@NotBlank String content) {}
    public record LikeRequest(@JsonProperty("liked") Boolean liked) {}
}
