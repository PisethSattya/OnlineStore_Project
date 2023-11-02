package co.devkh.onlinestore.reviewonlinestore.api.auth;

import co.devkh.onlinestore.reviewonlinestore.api.auth.web.AuthDto;
import co.devkh.onlinestore.reviewonlinestore.api.auth.web.LoginDto;
import co.devkh.onlinestore.reviewonlinestore.api.auth.web.RegisterDto;
import co.devkh.onlinestore.reviewonlinestore.api.auth.web.VerifyDto;
import co.devkh.onlinestore.reviewonlinestore.api.mail.Mail;
import co.devkh.onlinestore.reviewonlinestore.api.mail.MailService;
import co.devkh.onlinestore.reviewonlinestore.api.user.User;
import co.devkh.onlinestore.reviewonlinestore.api.user.UserService;
import co.devkh.onlinestore.reviewonlinestore.api.user.web.NewUserDto;
import co.devkh.onlinestore.reviewonlinestore.util.RandomUtil;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final UserService userService;
    private final AuthRepository authRepository;
    private final AuthMapper authMapper;
    private final MailService mailService;

    private final DaoAuthenticationProvider daoAuthenticationProvider;
    private final JwtEncoder jwtEncoder;


    @Value("${spring.mail.username}")
    private String adminMail;

    @Transactional
    @Override
    public void register(RegisterDto registerDto) throws MessagingException {
        NewUserDto newUserDto = authMapper.mapRegisterDtoToNewUserDto(registerDto);
        userService.createNewUser(newUserDto);

        String verifiedCode = RandomUtil.generateCode();

        // Store verifiedCode in database
        authRepository.updateVerifiedCode(registerDto.username(),verifiedCode);

        // Send verifiedCode via email
        Mail<String> verifiedMail = new Mail<>();
        verifiedMail.setSubject("Email Verification");
        verifiedMail.setSender(adminMail);
        verifiedMail.setReceiver(newUserDto.email());
        verifiedMail.setTemplate("auth/verify-mail");
        verifiedMail.setMetaData(verifiedCode);

        mailService.sendMail(verifiedMail);
    }
    @Transactional
    @Override
    public void verify(VerifyDto verifyDto) {

        User verifiedUser = authRepository.findByEmailAndVerifiedCodeAndIsDeletedFalse(verifyDto.email(),
                verifyDto.verifiedCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Verify email has been failed..!"));

        verifiedUser.setIsVerified(true);
        verifiedUser.setVerifiedCode(null);

        authRepository.save(verifiedUser);
    }

    @Override
    public AuthDto login(LoginDto loginDto) {

    Authentication auth =  new UsernamePasswordAuthenticationToken(loginDto.username(),loginDto.password());
    auth = daoAuthenticationProvider.authenticate(auth);

    log.info("AUTH = {}",auth.getName());
    log.info("AUTH = {}",auth.getAuthorities());
        Instant now = Instant.now();
        String scope = auth.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .collect(Collectors.joining(" "));


        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
                .id(auth.getName())
                .issuer("Public")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .subject("Access Token")
                .audience(List.of("Public Client"))
                .claim("scope",scope)
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsSet)).getTokenValue();

        return AuthDto.builder()
                .type("Bearer")
                .accessToken(accessToken)
                .build();
    }
}
