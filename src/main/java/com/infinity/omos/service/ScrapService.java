package com.infinity.omos.service;

import com.infinity.omos.domain.*;
import com.infinity.omos.domain.Posts.Posts;
import com.infinity.omos.domain.Posts.PostsRepository;
import com.infinity.omos.domain.Scrap.Scrap;
import com.infinity.omos.domain.Scrap.ScrapRepository;
import com.infinity.omos.domain.User.User;
import com.infinity.omos.domain.User.UserRepository;
import com.infinity.omos.dto.StateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScrapService {

    private final ScrapRepository scrapRepository;
    private final PostsRepository postsRepository;
    private final UserRepository userRepository;
    private final QueryRepository queryRepository;

    @Transactional
    public StateDto save(Long postsId, Long userId){
        Posts posts = postsRepository.findById(postsId).orElseThrow(() -> new RuntimeException("해당 레코드는 존재하지 않는 레코드입니다"));
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));
        if(queryRepository.existsScarpByUserIdPostId(user,posts)){
            throw new RuntimeException("이미 스크랩 되어있습니다");
        }

        scrapRepository.save(Scrap.builder().postId(posts).userId(user).build());
        return StateDto.builder().state(true).build();
    }

    @Transactional
    public StateDto delete(Long postsId, Long userId){
        Posts posts = postsRepository.findById(postsId).orElseThrow(() -> new RuntimeException("해당 레코드는 존재하지 않는 레코드입니다"));
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));

        scrapRepository.delete(queryRepository.findScrapByUserIdPostId(user,posts));
        return StateDto.builder().state(true).build();
    }

}
