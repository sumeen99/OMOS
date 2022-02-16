package com.infinity.omos.service;

import com.infinity.omos.api.createKakaoUser;
import com.infinity.omos.config.jwt.JwtTokenProvider;
import com.infinity.omos.domain.*;

import com.infinity.omos.dto.LoginDto;
import com.infinity.omos.dto.SignUpDto;
import com.infinity.omos.dto.TokenDto;
import com.infinity.omos.dto.UserResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final QueryRepository queryRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenDto login(LoginDto loginDto) {
        UsernamePasswordAuthenticationToken authenticationToken = loginDto.toAuthentication(); // ID/PW로 AuthenticationToken 생성
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken); // 사용자 비밀번호 체크, CustomUserDetailsService에서의 loadUserByUsername 메서드가 실행됨
        SecurityContextHolder.getContext().setAuthentication(authentication);//securityContext에 저장

        String email = authentication.getName();

        TokenDto tokenDto = jwtTokenProvider.createToken(authentication);
        RefreshToken refreshToken = RefreshToken.builder()
                .userEmail(email)
                .token(tokenDto.getRefreshToken())
                .build();

        tokenDto.updateId(queryRepository.findUserIdByUserEmail(email));
        refreshTokenRepository.save(refreshToken);
        return tokenDto;

    }

    @Transactional
    public UserResponseDto signUp(SignUpDto signUpDto) {
        if (userRepository.existsByEmail(signUpDto.getEmail())) {
            throw new RuntimeException("이미 가입되어 있는 유저입니다");
        }


        User user = User.toUser(signUpDto, Authority.ROLE_USER, passwordEncoder);

        return UserResponseDto.of(userRepository.save(user));
    }

    @Transactional
    public TokenDto reissue(TokenDto tokenDto) {

        // 2. Access Token 에서 User ID 가져오기
        Authentication authentication = jwtTokenProvider.getAuthentication(tokenDto.getAccessToken());

        // 1. Refresh Token 검증 -> 원래 spring batch나 redis로 주기적으로 만료된 refresh Token을 삭제해주어야하지만, 지금은 우선 유효하지 않을 경우, 저장소에서 검색후 삭제.
        if (!jwtTokenProvider.validateToken(tokenDto.getRefreshToken())) {
            refreshTokenRepository.deleteById(authentication.getName());
            throw new RuntimeException("Refresh Token 이 유효하지 않습니다.");
        }

        // 3. 저장소에서 User ID 를 기반으로 Refresh Token 값 가져옴
        RefreshToken refreshToken = refreshTokenRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("로그아웃 된 사용자입니다."));

        // 4. Refresh Token 일치하는지 검사
        if (!refreshToken.getToken().equals(tokenDto.getRefreshToken())) {
            throw new RuntimeException("토큰의 유저 정보가 일치하지 않습니다.");
        }

        // 5. 새로운 토큰 생성
        TokenDto newTokenDto = jwtTokenProvider.createToken(authentication);

        // 6. 저장소 정보 업데이트
        refreshToken.update(tokenDto.getRefreshToken());

        // 토큰 발급
        return tokenDto;
    }

    @Transactional
    public TokenDto kakaoLogin(String kakaoAccessToken) throws IOException {
        SignUpDto signUpDto=createKakaoUser.kakaoApi(kakaoAccessToken);
        if (userRepository.existsByEmail(signUpDto.getEmail())) { //존재하면 로그인해서 토큰주기
            return login(LoginDto.builder()
                    .email(signUpDto.getEmail())
                    .password(signUpDto.getPassword())
                    .build());
        }

        User user = User.toUser(signUpDto, Authority.ROLE_USER, passwordEncoder);
        userRepository.save(user);
        return null;

    }
}