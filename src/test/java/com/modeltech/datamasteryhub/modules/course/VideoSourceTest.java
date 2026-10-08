package com.modeltech.datamasteryhub.modules.course;

import com.modeltech.datamasteryhub.modules.course.service.VideoSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VideoSourceTest {

    @Test
    void vimeo_links_become_a_fixed_host_embed_with_the_privacy_hash() {
        VideoSource v = VideoSource.parse("https://vimeo.com/123456789/abcdef1234").orElseThrow();
        assertThat(v.provider()).isEqualTo(VideoSource.Provider.VIMEO);
        assertThat(v.embedUrl()).startsWith("https://player.vimeo.com/video/123456789?h=abcdef1234&dnt=1");

        assertThat(VideoSource.parse("https://player.vimeo.com/video/123456789?h=abcdef1234&autoplay=1").orElseThrow().hash())
                .isEqualTo("abcdef1234");
        assertThat(VideoSource.parse("https://vimeo.com/123456789").orElseThrow().embedUrl())
                .isEqualTo("https://player.vimeo.com/video/123456789?dnt=1&title=0&byline=0&portrait=0");
    }

    @Test
    void youtube_links_use_the_no_cookie_domain() {
        for (String url : new String[]{"https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10", "https://youtu.be/dQw4w9WgXcQ?si=x",
                "https://www.youtube.com/embed/dQw4w9WgXcQ", "https://youtube.com/shorts/dQw4w9WgXcQ"}) {
            assertThat(VideoSource.parse(url).orElseThrow().embedUrl())
                    .as(url).startsWith("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://video.example.com/intro.m3u8", "javascript:alert(1)", "https://vimeo.com.evil.test/123456789",
            "https://evil.test/watch?v=dQw4w9WgXcQ", "https://vimeo.com/channels/staffpicks", "https://www.youtube.com/watch?v=%22onload=x",
            "https://youtu.be/short", "not a url"})
    void anything_else_is_not_embedded(String url) {
        assertThat(VideoSource.parse(url)).isEmpty();
    }
}
