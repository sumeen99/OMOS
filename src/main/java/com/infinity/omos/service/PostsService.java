package com.infinity.omos.service;

import com.infinity.omos.api.SpotifyAllSearchApi;
import com.infinity.omos.api.SpotifyApiAuthorization;
import com.infinity.omos.domain.*;
import com.infinity.omos.domain.Posts.Posts;
import com.infinity.omos.domain.Posts.PostsRepository;
import com.infinity.omos.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.michaelthelin.spotify.SpotifyApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


@Service
@RequiredArgsConstructor
public class PostsService {

    private final PostsRepository postsRepository;
    private final QueryRepository queryRepository;
    private final LikeRepository likeRepository;
    private final ScrapRepository scrapRepository;
    private final SpotifyApiAuthorization spotifyApiAuthorization;
    private final UserRepository userRepository;
    private final MusicRepository musicRepository;


    @Transactional(readOnly = true)
    public HashMap<Category, List<PostsResponseDto>> selectRecordsMatchingAllCategory() {
        HashMap<Category, List<PostsResponseDto>> postsMatchingCategoryDtos = new HashMap<>();
        for (Category category : Category.values()) {
            postsMatchingCategoryDtos.put(category, selectRecordsMatchingCategory(category, 5));
        }

        return postsMatchingCategoryDtos;
    }

    @Transactional(readOnly = true)
    public List<PostsResponseDto> selectRecordsMatchingCategory(Category category, int size) {
        SpotifyApi spotifyApi = spotifyApiAuthorization.clientCredentials_Sync();

        List<Posts> posts = queryRepository.findPostsByCategory(category, size);
        List<PostsResponseDto> postsResponseDtos = new ArrayList<>();
        for (Posts post : posts) {
            TrackDto trackDto = SpotifyAllSearchApi.getTrackApi(spotifyApi.getAccessToken(), post.getMusicId().getId());

            postsResponseDtos.add(
                    PostsResponseDto.builder()
                            .music(MusicDto.builder()
                                    .musicId(trackDto.getMusicId())
                                    .musicTitle(trackDto.getMusicTitle())
                                    .albumImageUrl(trackDto.getAlbumImageUrl())
                                    .artists(trackDto.getArtists())
                                    .albumTitle(trackDto.getAlbumTitle())
                                    .build()
                            )
                            .recordId(post.getId())
                            .recordTitle(post.getTitle())
                            .recordImageUrl(post.getImageUrl())
                            .nickname(post.getUserId().getNickname())
                            .userId(post.getUserId().getId())
                            .build()
            );

        }

        return postsResponseDtos;
    }

    @Transactional(readOnly = true)
    public List<PostsDetailResponseDto> selectRecordsByCategory(Category category, SortType sortType, Long postId, int pageSize, Long userId) {
        SpotifyApi spotifyApi = spotifyApiAuthorization.clientCredentials_Sync();

        List<PostsDetailResponseDto> postsDetailResponseDtos = new ArrayList<>();
        List<Posts> posts;
        switch (sortType) {
            case date:
                posts = queryRepository.findAllByCategoryOrderByCreatedDate(category, postId, pageSize);
                break;
            case like:
                posts = queryRepository.findAllByCategoryOrderByLike(category, postId, pageSize);
                break;
            case random:
                posts = queryRepository.findAllByCategoryOrderByRandom(category, postId, pageSize);
                break;
            default:
                return postsDetailResponseDtos;

        }
        for (Posts post : posts) {
            TrackDto trackDto = SpotifyAllSearchApi.getTrackApi(spotifyApi.getAccessToken(), post.getMusicId().getId());
            User user = userRepository.getById(userId);
            postsDetailResponseDtos.add(
                    getPostsDetailResponseDto(post, user, trackDto)
            );
        }
        return postsDetailResponseDtos;
    }

    public enum SortType {
        date, random, like
    }

    @Transactional
    public StateDto save(PostsRequestDto requestDto) {
        if (!musicRepository.existsById(requestDto.getMusicId())) {
            musicRepository.save(Music.builder().id(requestDto.getMusicId()).build());
        }


        Posts posts = requestDto.toPosts(musicRepository.getById(requestDto.getMusicId()), userRepository.getById(requestDto.getUserId()));
        postsRepository.save(posts);

        return StateDto.builder().state(true).build();

    }

    @Transactional
    public StateDto plusViewsCnt(Long postsId) {
        Posts posts = postsRepository.findById(postsId).orElseThrow(() -> new RuntimeException("해당 레코드는 존재하지 않는 레코드입니다"));
        posts.updateCnt();
        return StateDto.builder().state(true).build();
    }


    static MusicDto getMusicDto(TrackDto trackDto) {
        return MusicDto.builder()
                .musicId(trackDto.getMusicId())
                .musicTitle(trackDto.getMusicTitle())
                .albumImageUrl(trackDto.getAlbumImageUrl())
                .artists(trackDto.getArtists())
                .albumTitle(trackDto.getAlbumTitle())
                .build();
    }

    PostsDetailResponseDto getPostsDetailResponseDto(Posts posts, User user, TrackDto trackDto) {
        return PostsDetailResponseDto.builder()
                .createdDate(posts.getCreatedDate())
                .recordTitle(posts.getTitle())
                .recordContents(posts.getContents())
                .recordId(posts.getId())
                .recordImageUrl(posts.getImageUrl())
                .viewsCnt(posts.getCnt())
                .userId(posts.getUserId().getId())
                .nickname(posts.getUserId().getNickname())
                .isLiked(queryRepository.existsLikeByUserIdPostId(user, posts))
                .isScraped(queryRepository.existsScarpByUserIdPostId(user, posts))
                .likeCnt(likeRepository.countByPostId(posts))
                .scrapCnt(scrapRepository.countByPostId(posts))
                .music(getMusicDto(trackDto))
                .category(posts.getCategory())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MyRecordDto> selectMyPosts(Long userId) {
        SpotifyApi spotifyApi = spotifyApiAuthorization.clientCredentials_Sync();

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));
        List<Posts> posts = queryRepository.findPostsByUserId(user);

        List<MyRecordDto> myRecordDtos = new ArrayList<>();
        for (Posts post : posts) {
            TrackDto trackDto = SpotifyAllSearchApi.getTrackApi(spotifyApi.getAccessToken(), post.getMusicId().getId());
            myRecordDtos.add(
                    MyRecordDto.builder()
                            .music(getMusicDto(trackDto))
                            .recordTitle(post.getTitle())
                            .recordContents(post.getContents())
                            .recordId(post.getId())
                            .createdDate(post.getCreatedDate())
                            .category(post.getCategory())
                            .isPublic(post.getIsPublic())
                            .build()

            );

        }
        return myRecordDtos;
    }

    @Transactional(readOnly = true)
    public List<PostsDetailResponseDto> selectMyPost(Long userId) {
        SpotifyApi spotifyApi = spotifyApiAuthorization.clientCredentials_Sync();

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));
        List<Long> postsIdList = queryRepository.findPostsIdByUserId(user); //이게 지금은 list를 다 받아와서 하는데 나중엔 하나씩받아와서 받아올때마다 dto만들고 의 반복으로 할 수 있을지 알아보자

        List<PostsDetailResponseDto> postsDetailResponseDtoList = new ArrayList<>();
        for (Long aLong : postsIdList) {

            Posts posts = postsRepository.getById(aLong);

            TrackDto trackDto = SpotifyAllSearchApi.getTrackApi(spotifyApi.getAccessToken(), posts.getMusicId().getId());

            PostsDetailResponseDto postsDetailResponseDto = getPostsDetailResponseDto(posts, user, trackDto);
            postsDetailResponseDto.setIsPublic(posts.getIsPublic());

            postsDetailResponseDtoList.add(postsDetailResponseDto);


        }


        return postsDetailResponseDtoList;
    }

    @Transactional
    public StateDto setPublic(Long postsId) {
        Posts posts = postsRepository.findById(postsId).orElseThrow(() -> new RuntimeException("해당 레코드는 존재하지 않는 레코드입니다"));
        posts.updatePublic();
        return StateDto.builder().state(true).build();
    }

    @Transactional
    public HashMap<String, Long> update(Long postsId, PostsUpdateDto postsUpdateDto) {
        Posts posts = postsRepository.findById(postsId).orElseThrow(() -> new RuntimeException("해당 레코드는 존재하지 않는 레코드입니다"));
        posts.updatePosts(postsUpdateDto.getTitle(), postsUpdateDto.getContents());

        HashMap<String, Long> postId = new HashMap<>();
        postId.put("postId", postsRepository.save(posts).getId());
        return postId;
    }

    @Transactional
    public StateDto delete(Long postsId) {
        Posts posts = postsRepository.findById(postsId).orElseThrow(() -> new RuntimeException("해당 레코드는 존재하지 않는 레코드입니다"));
        if (scrapRepository.existsByPostId(posts)) {
            scrapRepository.deleteAllByPostId(posts);
        }
        if (likeRepository.existsByPostId(posts)) {
            likeRepository.deleteAllByPostId(posts);
        }

        postsRepository.delete(posts);
        return StateDto.builder().state(true).build();
    }

    @Transactional(readOnly = true)
    public List<PostsDetailResponseDto> selectPostsByMusicId(String musicId, SortType sortType, Long postId, int pageSize, Long userId) {
        SpotifyApi spotifyApi = spotifyApiAuthorization.clientCredentials_Sync();

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));

        List<PostsDetailResponseDto> postsDetailResponseDtoList = new ArrayList<>();

        List<Posts> posts; //이게 지금은 list를 다 받아와서 하는데 나중엔 하나씩받아와서 받아올때마다 dto만들고 의 반복으로 할 수 있을지 알아보자
        switch (sortType) {
            case date:
                posts = queryRepository.findAllByMusicIdByCreatedDate(postId, musicId, pageSize);
                break;
            case like:
                posts = queryRepository.findAllByMusicIdByLike(postId, musicId, pageSize);
                break;
            case random:
                posts = queryRepository.findAllByMusicIdByRandom(postId, musicId, pageSize);
                break;
            default:
                return postsDetailResponseDtoList;

        }

        if (posts.isEmpty()) {//밑에서 posts.get(0) 땨문에 미리 걸러야함.
            return postsDetailResponseDtoList;
        }

        TrackDto trackDto = SpotifyAllSearchApi.getTrackApi(spotifyApi.getAccessToken(), posts.get(0).getMusicId().getId());//어차피 같은 뮤직아이디라서 한번만 조회하고 다 넣어주는게 좋을듯
        for (Posts post : posts) {

            PostsDetailResponseDto postsDetailResponseDto = getPostsDetailResponseDto(post, user, trackDto);

            postsDetailResponseDtoList.add(postsDetailResponseDto);

        }
        return postsDetailResponseDtoList;
    }

    @Transactional(readOnly = true)
    public List<PostsDetailResponseDto> selectPostsByUserId(Long fromUserId, Long toUserId) {
        SpotifyApi spotifyApi = spotifyApiAuthorization.clientCredentials_Sync();

        User fromUser = userRepository.findById(fromUserId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));
        User toUser = userRepository.findById(toUserId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));

        List<PostsDetailResponseDto> postsDetailResponseDtoList = new ArrayList<>();

        List<Posts> postsList = queryRepository.findPublicPostsByUserId(toUser);
        for (Posts post : postsList) {
            TrackDto trackDto = SpotifyAllSearchApi.getTrackApi(spotifyApi.getAccessToken(), post.getMusicId().getId());

            PostsDetailResponseDto postsDetailResponseDto = getPostsDetailResponseDto(post, fromUser, trackDto);

            postsDetailResponseDtoList.add(postsDetailResponseDto);

        }

        return postsDetailResponseDtoList;
    }

    @Transactional(readOnly = true)
    public List<PostsDetailResponseDto> selectMyDjPosts(Long userId, Long postId, int pageSize) {
        SpotifyApi spotifyApi = spotifyApiAuthorization.clientCredentials_Sync();

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));

        List<PostsDetailResponseDto> postsDetailResponseDtoList = new ArrayList<>();
        List<Posts> postsList = queryRepository.findAllMyDj(user, postId, pageSize);

        for (Posts post : postsList) {
            TrackDto trackDto = SpotifyAllSearchApi.getTrackApi(spotifyApi.getAccessToken(), post.getMusicId().getId());
            PostsDetailResponseDto postsDetailResponseDto = getPostsDetailResponseDto(post, user, trackDto);

            postsDetailResponseDtoList.add(postsDetailResponseDto);
        }
        return postsDetailResponseDtoList;
    }

    @Transactional(readOnly = true)
    public PostsDetailResponseDto selectPostById(Long postId, Long userId) {
        SpotifyApi spotifyApi = spotifyApiAuthorization.clientCredentials_Sync();

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("해당 유저는 존재하지 않는 유저입니다"));
        Posts post = postsRepository.findById(postId).orElseThrow(() -> new RuntimeException("해당 레코드는 존재하지 않는 레코드입니다"));

        TrackDto trackDto = SpotifyAllSearchApi.getTrackApi(spotifyApi.getAccessToken(), post.getMusicId().getId());
        PostsDetailResponseDto postsDetailResponseDto = getPostsDetailResponseDto(post, user, trackDto);
        if (user.getId().equals(post.getUserId().getId())) {
            postsDetailResponseDto.setIsPublic(post.getIsPublic());
        }

        return postsDetailResponseDto;
    }


}
