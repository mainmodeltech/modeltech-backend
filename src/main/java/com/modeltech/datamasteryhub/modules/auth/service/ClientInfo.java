package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import jakarta.servlet.http.HttpServletRequest;

/** D'où vient une connexion : adresse IP (voir {@link IpRateLimiter#clientIp}) et navigateur, pour la liste des sessions et le verrouillage. */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        return new ClientInfo(IpRateLimiter.clientIp(request), agent == null ? null : agent.substring(0, Math.min(agent.length(), 200)));
    }

    public static ClientInfo unknown() {
        return new ClientInfo("inconnue", null);
    }
}
