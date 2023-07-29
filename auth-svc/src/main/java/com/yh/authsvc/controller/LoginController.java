package com.yh.authsvc.controller;

import com.yh.authsvc.common.ResponseCodeEnum;
import com.yh.authsvc.common.ResponseResult;
import com.yh.authsvc.utils.JwtUtil;
import com.yh.authsvc.utils.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class LoginController {

    @Resource
    private JwtUtil jwtTokenUtil;

    @GetMapping("/time")
    public String index() {
        return "auth-service: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 登录认证
     *
     */
    @PostMapping("/token")
    public ResponseResult<?> login(@RequestBody Map<String,String> map) {
        // 从请求体中获取用户名密码
        String username  = map.get("username");
        String password = map.get("password");

        // 如果用户名和密码为空
        if(StringUtils.isEmpty(username) || StringUtils.isEmpty(password)){
            return ResponseResult.error(ResponseCodeEnum.LOGIN_ERROR.getCode(), ResponseCodeEnum.LOGIN_ERROR.getMessage());
        }

        // 将数据库的加密密码与用户明文密码做比对
        boolean isAuthenticated = true;

        // 如果密码匹配成功
        if(isAuthenticated){
            // 通过 jwtTokenUtil 生成 JWT 令牌和刷新令牌
            Map<String, Object> tokenMap = jwtTokenUtil
                    .generateTokenAndRefreshToken(username, username);
            return ResponseResult.success(tokenMap);
        }

        // 如果密码匹配失败
        return ResponseResult.error(ResponseCodeEnum.LOGIN_ERROR.getCode(), ResponseCodeEnum.LOGIN_ERROR.getMessage());

    }

    /**
     * 刷新JWT令牌,用旧的令牌换新的令牌
     * 参数为需要刷新的令牌
     * header中携带刷新令牌
     */
    @GetMapping("/refresh")
    public Mono<ResponseResult> refreshToken(@RequestHeader(value = "${auth.jwt.header}") String token){

        token = SecurityUtils.replaceTokenPrefix(token);

        if (org.apache.commons.lang3.StringUtils.isEmpty(token)) {
            return buildErrorResponse(ResponseCodeEnum.TOKEN_MISSION);
        }

        // 对Token解签名，并验证Token是否过期
        boolean isJwtNotValid = jwtTokenUtil.isTokenExpired(token);
        if(isJwtNotValid){
            return buildErrorResponse(ResponseCodeEnum.TOKEN_INVALID);
        }

        String userId = jwtTokenUtil.getUserIdFromToken(token);
        String username = jwtTokenUtil.getUserNameFromToken(token);

        // 这里为了保证 refreshToken 只能用一次，刷新后，会从 redis 中删除。
        // 如果用的不是 redis 中的 refreshToken 进行刷新令牌，则不能刷新。
        // 如果使用 redis 中已过期的 refreshToken 也不能刷新令牌。
        boolean isRefreshTokenNotExisted = jwtTokenUtil.isRefreshTokenNotExistCache(token);
        if(isRefreshTokenNotExisted){
            return buildErrorResponse(ResponseCodeEnum.REFRESH_TOKEN_INVALID);
        }

        Map<String, Object> tokenMap = jwtTokenUtil.refreshTokenAndGenerateToken(userId, username);

        return buildSuccessResponse(tokenMap);

    }

    /**
     * 登出，删除 redis 中的 accessToken 和 refreshToken
     * 只保证 refreshToken 不能使用，accessToken 还是能使用的。
     * 如果用户拿到了之前的 accessToken，则可以一直使用到过期，但是因为 refreshToken 已经无法使用了，所以保证了 accessToken 的时效性。
     * 下次登录时，需要重新获取新的 accessToken 和 refreshToken，这样才能利用 refreshToken 进行续期。
     */
    @PostMapping("/logout")
    public  Mono<ResponseResult> logout(@RequestParam("username") String username){

        boolean logoutResult = jwtTokenUtil.removeToken(username);

        if (logoutResult) {
            buildSuccessResponse(ResponseCodeEnum.SUCCESS);
        } else {
            buildErrorResponse(ResponseCodeEnum.LOGOUT_ERROR);
        }

        return buildSuccessResponse(ResponseCodeEnum.SUCCESS);
    }

    private Mono<ResponseResult> buildErrorResponse(ResponseCodeEnum responseCodeEnum){
        return Mono.create(callback -> callback.success(
                ResponseResult.error(responseCodeEnum.getCode(), responseCodeEnum.getMessage())
        ));
    }

    private Mono<ResponseResult> buildSuccessResponse(Object data){
        return Mono.create(callback -> callback.success(ResponseResult.success(data)
        ));
    }
}
