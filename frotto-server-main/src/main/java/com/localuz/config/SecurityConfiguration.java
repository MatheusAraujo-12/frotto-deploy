package com.localuz.config;

import com.localuz.security.*;
import com.localuz.security.jwt.*;
import com.localuz.security.ratelimit.RateLimitingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Import(SecurityProblemSupport.class)
public class SecurityConfiguration {

    private final TokenProvider tokenProvider;

    private final CorsFilter corsFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final SecurityProblemSupport problemSupport;

    public SecurityConfiguration(
        TokenProvider tokenProvider,
        CorsFilter corsFilter,
        RateLimitingFilter rateLimitingFilter,
        SecurityProblemSupport problemSupport
    ) {
        this.tokenProvider = tokenProvider;
        this.corsFilter = corsFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.problemSupport = problemSupport;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
            // Rate limiting for /api/authenticate and /api/register.
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exceptionHandling ->
                exceptionHandling.authenticationEntryPoint(problemSupport).accessDeniedHandler(problemSupport)
            )
            .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize ->
                authorize
                    .antMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .antMatchers("/swagger-ui/**")
                    .permitAll()
                    .antMatchers("/test/**")
                    .permitAll()
                    .antMatchers("/api/authenticate")
                    .permitAll()
                    .antMatchers("/api/register")
                    .permitAll()
                    .antMatchers("/api/activate")
                    .permitAll()
                    .antMatchers("/api/account/reset-password/init")
                    .permitAll()
                    .antMatchers("/api/account/reset-password/finish")
                    .permitAll()
                    .antMatchers(HttpMethod.POST, "/api/webhooks/mercadopago")
                    .permitAll()
                    .antMatchers("/api/admin/**")
                    .hasAuthority(AuthoritiesConstants.ADMIN)
                    .antMatchers("/api/**")
                    .authenticated()
                    .antMatchers("/management/health")
                    .permitAll()
                    .antMatchers("/management/health/**")
                    .permitAll()
                    .antMatchers("/management/info")
                    .permitAll()
                    .antMatchers("/management/prometheus")
                    .permitAll()
                    .antMatchers("/management/**")
                    .hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .httpBasic(Customizer.withDefaults())
            .apply(securityConfigurerAdapter());
        return http.build();
        // @formatter:on
    }

    private JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider);
    }
}
