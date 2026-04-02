# OAuth2 Social Login Setup Guide

## Overview
OAuth2 social login integration supporting Google and GitHub providers.
Authenticates users via OAuth2 and issues JWT tokens compatible with the existing auth system.

## Architecture

### Flow
1. Client redirects to `/oauth2/authorization/{provider}` (google or github)
2. User authenticates with the social provider
3. `CustomOAuth2UserService` processes the OAuth2 user info
   - New user → creates account
   - Existing OAuth2 user → updates name
   - Existing local user with same email → links OAuth2 provider
4. `OAuth2AuthenticationSuccessHandler` issues JWT access + refresh tokens
5. Redirects to `{OAUTH2_REDIRECT_URI}?accessToken=...&refreshToken=...`

### Files
| File | Description |
|------|-------------|
| `config/SecurityConfig.java` | OAuth2 login integrated into security filter chain |
| `config/OAuth2AuthenticationSuccessHandler.java` | JWT token issuance on OAuth2 success |
| `service/CustomOAuth2UserService.java` | OAuth2 user processing (create/link/update) |
| `dto/OAuth2UserInfo.java` | Provider-specific user info extraction |
| `domain/User.java` | Added `provider`, `providerId` fields and `AuthProvider` enum |
| `repository/UserRepository.java` | Added `findByProviderAndProviderId()` query |

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `OAUTH2_GOOGLE_CLIENT_ID` | Google OAuth2 Client ID | Yes (for Google) |
| `OAUTH2_GOOGLE_CLIENT_SECRET` | Google OAuth2 Client Secret | Yes (for Google) |
| `OAUTH2_GITHUB_CLIENT_ID` | GitHub OAuth2 Client ID | Yes (for GitHub) |
| `OAUTH2_GITHUB_CLIENT_SECRET` | GitHub OAuth2 Client Secret | Yes (for GitHub) |
| `OAUTH2_REDIRECT_URI` | Frontend callback URL | No (default: `http://localhost:3000/oauth2/callback`) |

## Google Setup
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create OAuth2 credentials (Web application)
3. Add authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
4. Set `OAUTH2_GOOGLE_CLIENT_ID` and `OAUTH2_GOOGLE_CLIENT_SECRET`

## GitHub Setup
1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Create a new OAuth App
3. Set Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
4. Set `OAUTH2_GITHUB_CLIENT_ID` and `OAUTH2_GITHUB_CLIENT_SECRET`

## Frontend Integration

### Initiate Login
```javascript
// Redirect user to start OAuth2 flow
window.location.href = '/oauth2/authorization/google';
// or
window.location.href = '/oauth2/authorization/github';
```

### Handle Callback
```javascript
// On /oauth2/callback page
const params = new URLSearchParams(window.location.search);
const accessToken = params.get('accessToken');
const refreshToken = params.get('refreshToken');

// Store tokens and use for API requests
localStorage.setItem('accessToken', accessToken);
localStorage.setItem('refreshToken', refreshToken);
```

## Database Changes
Two new columns added to `users` table (auto-created via JPA ddl-auto):
- `provider` (VARCHAR) - `LOCAL`, `GOOGLE`, or `GITHUB`
- `provider_id` (VARCHAR) - Provider-specific user ID
