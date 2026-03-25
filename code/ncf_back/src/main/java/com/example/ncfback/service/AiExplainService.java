package com.example.ncfback.service;

import com.example.ncfback.entity.Item;
import com.example.ncfback.entity.RecommendationView;
import com.example.ncfback.entity.User;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
public class AiExplainService {

    public String explain(User user, Item item, RecommendationView recommendationView) {
        String username = user.getExternalUserNo() == null || user.getExternalUserNo().isBlank()
                ? String.valueOf(user.getUserId())
                : user.getExternalUserNo();
        String reason = recommendationView.getReasonText();
        if (reason == null || reason.isBlank()) {
            reason = "系统基于你的近期行为、收藏、关注关系和内容偏好生成了这条推荐。";
        }

        double score = recommendationView.getScore() == null ? 0.0d : recommendationView.getScore();
        int rank = recommendationView.getRankNo() == null ? 0 : recommendationView.getRankNo();
        boolean coldStart = reason.contains("热门") || reason.contains("Top10") || reason.contains("冷启动");

        String content;
        if (coldStart) {
            content = String.format(
                    "给用户%s推荐《%s》的原因：%s 当前歌曲为《%s》，演唱者是%s。当前排序第%d位，展示分数为%.3f。你目前仍处于冷启动阶段，因此系统先展示平台热门内容，后续会结合真实播放、收藏、评分、好友关注和反馈工单逐步切换到个性化推荐。",
                    username,
                    item.getTitle(),
                    reason,
                    item.getTitle(),
                    item.getArtistName(),
                    rank,
                    score
            );
        } else {
            content = String.format(
                    "给用户%s推荐《%s》的原因：%s 当前歌曲为《%s》，演唱者是%s。模型分数为%.3f，当前排序第%d位。该解释综合参考了最近播放、收藏、评分、好友关注、内容偏好以及用户提交的反馈工单信号。",
                    username,
                    item.getTitle(),
                    reason,
                    item.getTitle(),
                    item.getArtistName(),
                    score,
                    rank
            );
        }

        return new Document(content).getContent();
    }
}
