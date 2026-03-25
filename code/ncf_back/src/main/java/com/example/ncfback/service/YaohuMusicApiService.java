package com.example.ncfback.service;

import com.example.ncfback.config.YaohuMusicApiProperties;
import com.example.ncfback.dto.YaohuMusicSearchResponse;
import com.example.ncfback.entity.Item;
import com.example.ncfback.entity.ItemMedia;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class YaohuMusicApiService {

    private static final int SUCCESS_CODE = 200;
    private static final List<Integer> COMMON_LOCAL_PROXY_PORTS = List.of(7890);
    private static final int MAX_LYRIC_SNIPPET_LENGTH = 240;

    private final YaohuMusicApiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();
    private final HttpClient directMediaHttpClient = buildHttpClient(new DirectProxySelector());
    private final HttpClient defaultMediaHttpClient = buildHttpClient(null);

    public Optional<YaohuMusicSearchResponse> search(String source, String query, Integer count) {
        String normalizedSource = normalizeSource(source);
        String baseUrl = resolveBaseUrl(normalizedSource);
        if (!StringUtils.hasText(properties.getKey()) || !StringUtils.hasText(query) || !StringUtils.hasText(baseUrl)) {
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("key", properties.getKey())
                .queryParam("msg", query)
                .queryParam("g", normalizeCount(count))
                .encode()
                .build()
                .toUri();

        try {
            String payload = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.ACCEPT, "application/json, text/json, */*")
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(payload)) {
                log.warn("yaohu search returned empty response body for source={}, query={}", normalizedSource, query);
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(payload);
            if (!isSuccess(root)) {
                log.warn("yaohu search returned non-success payload for source={}, query={}", normalizedSource, query);
                return Optional.empty();
            }
            return Optional.of(parseSearchResponse(normalizedSource, query, root));
        } catch (Exception ex) {
            log.warn("Failed to fetch search result from yaohu for source={}, query={}: {}", normalizedSource, query, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<TrackDetail> fetchTrackDetail(String source, String query, Integer index, String level) {
        String normalizedSource = normalizeSource(source);
        String baseUrl = resolveBaseUrl(normalizedSource);
        if (!StringUtils.hasText(properties.getKey()) || !StringUtils.hasText(query) || index == null || index <= 0 || !StringUtils.hasText(baseUrl)) {
            return Optional.empty();
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("key", properties.getKey())
                .queryParam("msg", query)
                .queryParam("n", index);
        if ("wyvip".equals(normalizedSource)) {
            builder.queryParam("level", normalizeLevel(level));
        }

        URI uri = builder.encode().build().toUri();
        try {
            String payload = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.ACCEPT, "application/json, text/json, */*")
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(payload)) {
                log.warn("yaohu track detail returned empty response body for source={}, query={}, index={}", normalizedSource, query, index);
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(payload);
            if (!isSuccess(root)) {
                log.warn("yaohu track detail returned non-success payload for source={}, query={}, index={}", normalizedSource, query, index);
                return Optional.empty();
            }
            return Optional.of(parseTrackDetail(normalizedSource, query, index, level, root));
        } catch (Exception ex) {
            log.warn("Failed to fetch track detail from yaohu for source={}, query={}, index={}: {}", normalizedSource, query, index, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ItemMedia> fetchPreview(Item item, ItemMedia existingMedia) {
        return fetchPreview(item, existingMedia, null);
    }

    public Optional<ItemMedia> fetchPreview(Item item, ItemMedia existingMedia, String preferredSource) {
        TrackSelector selector = resolveTrackSelector(item).orElse(null);
        String normalizedPreferredSource = normalizeSource(preferredSource);
        if (selector != null && (!StringUtils.hasText(preferredSource) || selector.source().equals(normalizedPreferredSource))) {
            Optional<ItemMedia> directMedia = fetchTrackDetail(selector.source(), selector.query(), selector.index(), properties.getDefaultLevel())
                    .filter(detail -> StringUtils.hasText(detail.musicUrl()))
                    .map(detail -> buildMedia(item.getItemId(), detail, existingMedia));
            if (directMedia.isPresent()) {
                return directMedia;
            }
        }

        if (item == null || !StringUtils.hasText(item.getTitle())) {
            return Optional.empty();
        }

        for (String source : candidateSources(item, item.getTitle(), item.getAlbumName(), item.getArtistName(), preferredSource)) {
            for (String query : candidateQueries(item)) {
                Optional<ItemMedia> resolved = fetchPreviewBySearch(source, query, item, existingMedia);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ItemMedia> fetchPreviewBySearch(String source,
                                                     String query,
                                                     Item item,
                                                     ItemMedia existingMedia) {
        Optional<YaohuMusicSearchResponse> searchResponse = search(source, query, 5);
        if (searchResponse.isEmpty() || searchResponse.get().getSongs().isEmpty()) {
            return Optional.empty();
        }
        YaohuMusicSearchResponse.SongOption matchedSong = findBestMatch(item, searchResponse.get().getSongs());
        return fetchTrackDetail(source, query, matchedSong.getIndex(), properties.getDefaultLevel())
                .filter(detail -> StringUtils.hasText(detail.musicUrl()))
                .map(detail -> buildMedia(item.getItemId(), detail, existingMedia));
    }

    public Optional<TrackDetail> fetchTrackDetailByItem(Item item, String level) {
        if (item == null) {
            return Optional.empty();
        }
        return fetchTrackDetailByMetadata(item, item.getTitle(), item.getAlbumName(), item.getArtistName(), level, null);
    }

    public Optional<TrackDetail> fetchTrackDetailByMetadata(Item item,
                                                            String title,
                                                            String album,
                                                            String artist,
                                                            String level) {
        return fetchTrackDetailByMetadata(item, title, album, artist, level, null);
    }

    public Optional<TrackDetail> fetchTrackDetailByMetadata(Item item,
                                                            String title,
                                                            String album,
                                                            String artist,
                                                            String level,
                                                            String preferredSource) {
        Optional<TrackSelector> selector = resolveTrackSelector(item);
        String normalizedPreferredSource = normalizeSource(preferredSource);
        if (selector.isPresent() && (!StringUtils.hasText(preferredSource) || selector.get().source().equals(normalizedPreferredSource))) {
            Optional<TrackDetail> detail = fetchTrackDetail(selector.get().source(), selector.get().query(), selector.get().index(), level);
            if (detail.isPresent()) {
                return detail;
            }
        }

        if (!StringUtils.hasText(title) && !StringUtils.hasText(artist)) {
            return Optional.empty();
        }

        for (String source : candidateSources(item, title, album, artist, preferredSource)) {
            for (String query : candidateQueries(item, title, album, artist)) {
                Optional<YaohuMusicSearchResponse> searchResponse = search(source, query, 10);
                if (searchResponse.isEmpty() || searchResponse.get().getSongs().isEmpty()) {
                    continue;
                }
                YaohuMusicSearchResponse.SongOption matchedSong =
                        findBestMatch(item, title, album, artist, searchResponse.get().getSongs());
                Optional<TrackDetail> detail = fetchTrackDetail(source, query, matchedSong.getIndex(), level);
                if (detail.isPresent()) {
                    return detail;
                }
            }
        }
        return Optional.empty();
    }

    public ItemMedia buildMedia(Long itemId, TrackDetail detail, ItemMedia existingMedia) {
        ItemMedia media = new ItemMedia();
        media.setItemId(itemId);
        media.setPreviewUrl(detail.musicUrl());
        media.setCoverUrl(StringUtils.hasText(detail.coverUrl()) ? detail.coverUrl() : existingMedia != null ? existingMedia.getCoverUrl() : null);
        media.setPreviewDurationSeconds(existingMedia != null && existingMedia.getPreviewDurationSeconds() != null
                ? existingMedia.getPreviewDurationSeconds()
                : 30);
        media.setLyricSnippet(sanitizeLyricSnippet(StringUtils.hasText(detail.lyricText())
                ? detail.lyricText()
                : detail.name() + " - " + detail.singer() + (StringUtils.hasText(detail.album()) ? " / " + detail.album() : "")));
        media.setSourcePlatform(buildSourcePlatformPrefix(detail.source()) + detail.qualityLevel().toUpperCase(Locale.ROOT));
        return media;
    }

    public static String buildCatalogKey(String source, String query, Integer index) {
        String normalizedSource = normalizeSourceStatic(source);
        return "YAOHU_" + normalizedSource.toUpperCase(Locale.ROOT) + "_QUERY::" + query + "::" + (index == null || index <= 0 ? 1 : index);
    }

    public boolean shouldRefresh(ItemMedia media) {
        if (media == null) {
            return true;
        }
        if (!StringUtils.hasText(media.getPreviewUrl())) {
            return true;
        }
        if (!StringUtils.hasText(media.getSourcePlatform())) {
            return true;
        }
        if ("DEMO_CLIP".equalsIgnoreCase(media.getSourcePlatform())) {
            return true;
        }
        if (media.getUpdatedAt() == null) {
            return true;
        }
        return media.getUpdatedAt().plusHours(properties.getRefreshHours()).isBefore(java.time.LocalDateTime.now());
    }

    public MediaStreamResponse downloadMedia(String mediaUrl, String rangeHeader) {
        if (!StringUtils.hasText(mediaUrl)) {
            throw new IllegalArgumentException("mediaUrl cannot be blank");
        }

        List<HttpRequest> requests = buildMediaRequests(mediaUrl, rangeHeader);
        Exception lastException = null;
        for (HttpRequest request : requests) {
            for (MediaClientCandidate candidate : buildMediaClientCandidates()) {
                try {
                    return executeDownload(candidate.client(), request);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Failed to download upstream media", ex);
                } catch (Exception ex) {
                    lastException = ex;
                    log.warn("{} media fetch failed: {}", candidate.name(), ex.getMessage());
                }
            }
        }

        throw new IllegalStateException("Failed to download upstream media", lastException);
    }

    public String normalizeSource(String source) {
        return normalizeSourceStatic(source);
    }

    public String normalizeLevel(String level) {
        String normalized = StringUtils.hasText(level) ? level.trim().toLowerCase(Locale.ROOT) : properties.getDefaultLevel();
        return switch (normalized) {
            case "standard", "exhigh", "lossless", "hires", "hi-res", "320kbps", "hq" ->
                    switch (normalized) {
                        case "320kbps", "hq" -> "exhigh";
                        case "hi-res" -> "hires";
                        default -> normalized;
                    };
            default -> properties.getDefaultLevel();
        };
    }

    private String resolveBaseUrl(String source) {
        return switch (source) {
            case "qq_plus" -> properties.getQqPlus().getBaseUrl();
            case "wyvip" -> properties.getWyvip().getBaseUrl();
            default -> properties.getWyvip().getBaseUrl();
        };
    }

    private YaohuMusicSearchResponse parseSearchResponse(String source, String query, JsonNode root) {
        YaohuMusicSearchResponse response = new YaohuMusicSearchResponse();
        response.setCode(root.path("code").asInt());
        response.setMsg(root.path("msg").asText(null));
        response.setTips(root.path("tips").asText(null));
        response.setQuery(query);
        response.setSource(source);

        JsonNode songs = root.path("data").path("songs");
        if (songs.isArray()) {
            for (JsonNode songNode : songs) {
                YaohuMusicSearchResponse.SongOption song = new YaohuMusicSearchResponse.SongOption();
                song.setIndex(songNode.path("n").asInt());
                song.setName(songNode.path("name").asText(""));
                song.setSinger(songNode.path("singer").asText(""));
                song.setAlbum(songNode.path("album").asText(""));
                song.setPayTag(songNode.path("pay").asText(""));
                song.setMid(songNode.path("mid").asText(""));
                song.setCoverUrl(firstTextFlexible(songNode, "picture", "pic", "cover", "coverUrl", "img"));
                song.setLyricText(firstTextFlexible(songNode, "lrctxt", "lrc", "lyric", "lyricText", "lyrics"));
                response.getSongs().add(song);
            }
        }
        return response;
    }

    private TrackDetail parseTrackDetail(String source, String query, Integer index, String level, JsonNode root) {
        JsonNode data = root.path("data");
        return "qq_plus".equals(source)
                ? parseQqTrackDetail(query, index, data)
                : parseWyTrackDetail(query, index, level, data);
    }

    private TrackDetail parseWyTrackDetail(String query, Integer index, String level, JsonNode data) {
        JsonNode vipmusic = data.path("vipmusic");
        JsonNode music = data.path("music");
        String musicUrl = firstNonBlank(
                firstTextFlexible(vipmusic, "url", "playUrl"),
                firstTextFlexible(data, "url", "musicurl", "music_url", "playUrl")
        );
        return new TrackDetail(
                "wyvip",
                index,
                firstNonBlank(firstTextFlexible(data, "name", "songname", "title"), query),
                firstNonBlank(firstTextFlexible(data, "singer", "artist", "author"), ""),
                firstNonBlank(firstTextFlexible(data, "album", "special", "albumname"), ""),
                musicUrl,
                firstNonBlank(firstTextFlexible(data, "picture", "pic", "cover", "coverUrl", "img"), ""),
                normalizeLevel(firstNonBlank(firstTextFlexible(vipmusic, "level"), level, properties.getDefaultLevel())),
                firstNonBlank(
                        firstTextFlexible(music, "lrc", "lyric", "lyrics", "lrctxt"),
                        firstTextFlexible(data, "lrc", "lyric", "lyrics", "lyricText", "lrctxt"),
                        ""
                ),
                ""
        );
    }

    private TrackDetail parseQqTrackDetail(String query, Integer index, JsonNode data) {
        JsonNode musicUrlNode = data.path("music_url");
        String musicUrl = firstNonBlank(
                firstTextFlexible(musicUrlNode, "url", "playUrl"),
                firstTextFlexible(data, "musicurl", "music_url", "url", "playUrl")
        );
        String quality = firstNonBlank(
                firstTextFlexible(musicUrlNode, "bitrate", "level", "quality", "actual_size"),
                firstTextFlexible(data, "bitrate", "level", "quality"),
                "320kbps"
        );
        return new TrackDetail(
                "qq_plus",
                index,
                firstNonBlank(firstTextFlexible(data, "name", "songname", "title"), query),
                firstNonBlank(firstTextFlexible(data, "singer", "artist", "author"), ""),
                firstNonBlank(firstTextFlexible(data, "album", "special", "albumname"), ""),
                musicUrl,
                firstNonBlank(firstTextFlexible(data, "picture", "pic", "cover", "coverUrl", "img"), ""),
                normalizeQqQuality(quality),
                firstNonBlank(firstTextFlexible(data, "lrctxt", "lrc", "lyric", "lyricText", "lyrics"), ""),
                firstNonBlank(firstTextFlexible(data, "pay", "payTag"), "")
        );
    }

    private String normalizeQqQuality(String raw) {
        String value = StringUtils.hasText(raw) ? raw.toLowerCase(Locale.ROOT) : "";
        if (value.contains("hires") || value.contains("hi-res")) {
            return "hires";
        }
        if (value.contains("320") || value.contains("hq")) {
            return "exhigh";
        }
        if (value.contains("lossless") || value.contains("flac")) {
            return "lossless";
        }
        return "standard";
    }

    private YaohuMusicSearchResponse.SongOption findBestMatch(Item item, List<YaohuMusicSearchResponse.SongOption> songs) {
        return findBestMatch(item, item != null ? item.getTitle() : null, item != null ? item.getAlbumName() : null,
                item != null ? item.getArtistName() : null, songs);
    }

    private YaohuMusicSearchResponse.SongOption findBestMatch(Item item,
                                                              String title,
                                                              String album,
                                                              String artist,
                                                              List<YaohuMusicSearchResponse.SongOption> songs) {
        YaohuMusicSearchResponse.SongOption best = null;
        int bestScore = Integer.MIN_VALUE;
        for (YaohuMusicSearchResponse.SongOption song : songs) {
            int score = matchScore(item, title, album, artist, song);
            if (score > bestScore) {
                best = song;
                bestScore = score;
            }
        }
        if (best != null) {
            return best;
        }
        for (YaohuMusicSearchResponse.SongOption song : songs) {
            if (equalsIgnoreCaseTrimmed(song.getName(), title)
                    && equalsIgnoreCaseTrimmed(song.getSinger(), artist)) {
                return song;
            }
        }
        return songs.get(0);
    }

    private int matchScore(Item item,
                           String title,
                           String album,
                           String artist,
                           YaohuMusicSearchResponse.SongOption song) {
        if (song == null) {
            return 0;
        }

        String targetTitle = StringUtils.hasText(title) ? title : item != null ? item.getTitle() : null;
        String targetArtist = StringUtils.hasText(artist) ? artist : item != null ? item.getArtistName() : null;
        String targetAlbum = StringUtils.hasText(album) ? album : item != null ? item.getAlbumName() : null;

        int score = 0;
        if (equalsIgnoreCaseTrimmed(song.getName(), targetTitle)) {
            score += 10;
        } else if (containsIgnoreCase(song.getName(), targetTitle) || containsIgnoreCase(targetTitle, song.getName())) {
            score += 4;
        }

        if (equalsIgnoreCaseTrimmed(song.getSinger(), targetArtist)) {
            score += 6;
        } else if (containsIgnoreCase(song.getSinger(), targetArtist) || containsIgnoreCase(targetArtist, song.getSinger())) {
            score += 2;
        }

        if (StringUtils.hasText(targetAlbum) && equalsIgnoreCaseTrimmed(song.getAlbum(), targetAlbum)) {
            score += 2;
        }
        return score;
    }

    private Optional<TrackSelector> resolveTrackSelector(Item item) {
        if (item == null || !StringUtils.hasText(item.getExternalItemNo()) || !item.getExternalItemNo().startsWith("YAOHU_")) {
            return Optional.empty();
        }
        String externalItemNo = item.getExternalItemNo();
        int queryIndex = externalItemNo.indexOf("_QUERY::");
        if (queryIndex < 0) {
            return Optional.empty();
        }
        String source = externalItemNo.substring("YAOHU_".length(), queryIndex).toLowerCase(Locale.ROOT);
        String payload = externalItemNo.substring(queryIndex + "_QUERY::".length());
        int separatorIndex = payload.lastIndexOf("::");
        if (separatorIndex < 0) {
            return Optional.of(new TrackSelector(source, payload, 1));
        }
        String query = payload.substring(0, separatorIndex);
        String indexText = payload.substring(separatorIndex + 2);
        try {
            return Optional.of(new TrackSelector(source, query, Integer.parseInt(indexText)));
        } catch (NumberFormatException ex) {
            return Optional.of(new TrackSelector(source, query, 1));
        }
    }

    private int normalizeCount(Integer count) {
        int resolved = count == null ? properties.getDefaultSearchCount() : count;
        if (resolved <= 0) {
            return properties.getDefaultSearchCount();
        }
        return Math.min(resolved, 30);
    }

    private boolean isSuccess(JsonNode root) {
        return root != null && root.path("code").asInt() == SUCCESS_CODE;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode child = node.path(fieldName);
            if (child.isValueNode()) {
                String text = child.asText();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private List<HttpRequest> buildMediaRequests(String mediaUrl, String rangeHeader) {
        List<HttpRequest> requests = new ArrayList<>();
        URI uri = URI.create(mediaUrl);

        requests.add(buildMediaRequest(uri, rangeHeader, resolveReferer(uri), resolveOrigin(uri)));
        requests.add(buildMediaRequest(uri, rangeHeader, null, null));
        return requests;
    }

    private HttpRequest buildMediaRequest(URI uri, String rangeHeader, String referer, String origin) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity");

        if (StringUtils.hasText(referer)) {
            requestBuilder.header("Referer", referer);
        }
        if (StringUtils.hasText(origin)) {
            requestBuilder.header("Origin", origin);
        }
        if (StringUtils.hasText(rangeHeader)) {
            requestBuilder.header("Range", rangeHeader);
        }
        return requestBuilder.build();
    }

    private String resolveReferer(URI uri) {
        String host = uri != null ? uri.getHost() : null;
        if (!StringUtils.hasText(host)) {
            return null;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.contains("qq.com") || normalizedHost.contains("tencent")) {
            return "https://y.qq.com/";
        }
        if (normalizedHost.contains("163.com") || normalizedHost.contains("126.net")) {
            return "https://music.163.com/";
        }
        return uri.getScheme() + "://" + host + "/";
    }

    private String resolveOrigin(URI uri) {
        String host = uri != null ? uri.getHost() : null;
        if (!StringUtils.hasText(host) || !StringUtils.hasText(uri.getScheme())) {
            return null;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.contains("qq.com") || normalizedHost.contains("tencent")) {
            return "https://y.qq.com";
        }
        if (normalizedHost.contains("163.com") || normalizedHost.contains("126.net")) {
            return "https://music.163.com";
        }
        return uri.getScheme() + "://" + host;
    }

    private String firstTextFlexible(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode child = node.path(fieldName);
            String text = flattenTextValue(child);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String flattenTextValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            String text = node.asText();
            return StringUtils.hasText(text) ? text : null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String text = flattenTextValue(child);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            return null;
        }
        if (node.isObject()) {
            for (String nestedField : List.of(
                    "url", "text", "value", "lyric", "lyrics", "lrc", "lrctxt",
                    "picture", "pic", "cover", "coverUrl", "img", "src",
                    "name", "title", "artist", "singer", "author", "album", "special",
                    "level", "quality", "bitrate", "actual_size")) {
                String text = flattenTextValue(node.path(nestedField));
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public String sanitizeLyricSnippet(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value
                .replace("\r", "\n")
                .replaceAll("\\[\\d{2}:\\d{2}(?:\\.\\d{1,3})?]", " ")
                .replaceAll("\\s*\\n\\s*", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (normalized.length() <= MAX_LYRIC_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LYRIC_SNIPPET_LENGTH - 3).trim() + "...";
    }

    private boolean equalsIgnoreCaseTrimmed(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean containsIgnoreCase(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().toLowerCase(Locale.ROOT).contains(right.trim().toLowerCase(Locale.ROOT));
    }

    private List<String> candidateSources(Item item) {
        return candidateSources(item, item != null ? item.getTitle() : null,
                item != null ? item.getAlbumName() : null, item != null ? item.getArtistName() : null, null);
    }

    private List<String> candidateSources(Item item, String title, String album, String artist, String preferredSource) {
        List<String> sources = new ArrayList<>();
        resolveTrackSelector(item).ifPresent(selector -> sources.add(selector.source()));
        if (!sources.contains("wyvip")) {
            sources.add("wyvip");
        }
        if (!sources.contains("qq_plus")) {
            sources.add("qq_plus");
        }
        if (StringUtils.hasText(preferredSource)) {
            String normalizedPreferredSource = normalizeSource(preferredSource);
            sources.remove(normalizedPreferredSource);
            sources.add(0, normalizedPreferredSource);
        }
        return sources;
    }

    private List<String> candidateQueries(Item item) {
        return candidateQueries(item, item != null ? item.getTitle() : null,
                item != null ? item.getAlbumName() : null, item != null ? item.getArtistName() : null);
    }

    private List<String> candidateQueries(Item item, String title, String album, String artist) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (item == null && !StringUtils.hasText(title) && !StringUtils.hasText(album) && !StringUtils.hasText(artist)) {
            return List.of();
        }

        String resolvedTitle = StringUtils.hasText(title) ? title : item != null ? item.getTitle() : null;
        String resolvedAlbum = StringUtils.hasText(album) ? album : item != null ? item.getAlbumName() : null;
        String resolvedArtist = StringUtils.hasText(artist) ? artist : item != null ? item.getArtistName() : null;

        addQuery(queries, resolvedTitle);
        addQuery(queries, joinNonBlank(resolvedTitle, resolvedArtist));
        addQuery(queries, joinNonBlank(resolvedArtist, resolvedTitle));
        addQuery(queries, joinNonBlank(resolvedTitle, resolvedAlbum));
        return new ArrayList<>(queries);
    }

    private void addQuery(Set<String> queries, String query) {
        if (StringUtils.hasText(query)) {
            queries.add(query.trim());
        }
    }

    private String joinNonBlank(String left, String right) {
        if (StringUtils.hasText(left) && StringUtils.hasText(right)) {
            return left.trim() + " " + right.trim();
        }
        return StringUtils.hasText(left) ? left.trim() : StringUtils.hasText(right) ? right.trim() : null;
    }

    private String buildSourcePlatformPrefix(String source) {
        return "YAOHU_" + normalizeSource(source).toUpperCase(Locale.ROOT) + "_";
    }

    private static String normalizeSourceStatic(String source) {
        if (!StringUtils.hasText(source)) {
            return "wyvip";
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "qq", "qqplus", "qq_plus" -> "qq_plus";
            case "wy", "wyvip", "netease" -> "wyvip";
            default -> "wyvip";
        };
    }

    private MediaStreamResponse executeDownload(HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Upstream media request failed with status " + response.statusCode());
        }
        return new MediaStreamResponse(response.statusCode(), response.headers(), response.body());
    }

    public record MediaStreamResponse(int statusCode, java.net.http.HttpHeaders headers, byte[] body) {
    }

    public record TrackDetail(String source,
                              Integer index,
                              String name,
                              String singer,
                              String album,
                              String musicUrl,
                              String coverUrl,
                              String qualityLevel,
                              String lyricText,
                              String payTag) {
    }

    private record TrackSelector(String source, String query, Integer index) {
    }

    private List<MediaClientCandidate> buildMediaClientCandidates() {
        List<MediaClientCandidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addClientCandidate(candidates, seen, "configured proxy", buildConfiguredProxyClient());
        addClientCandidate(candidates, seen, "environment proxy", buildEnvironmentProxyClient());
        addClientCandidate(candidates, seen, "localhost:7890 proxy", buildCommonLocalProxyClient());
        addClientCandidate(candidates, seen, "direct", directMediaHttpClient);
        addClientCandidate(candidates, seen, "default", defaultMediaHttpClient);
        return candidates;
    }

    private void addClientCandidate(List<MediaClientCandidate> candidates, Set<String> seen, String name, HttpClient client) {
        if (client == null || !seen.add(name)) {
            return;
        }
        candidates.add(new MediaClientCandidate(name, client));
    }

    private HttpClient buildConfiguredProxyClient() {
        if (!StringUtils.hasText(properties.getMediaProxyHost()) || properties.getMediaProxyPort() == null || properties.getMediaProxyPort() <= 0) {
            return null;
        }
        return buildProxyHttpClient(properties.getMediaProxyHost(), properties.getMediaProxyPort());
    }

    private HttpClient buildEnvironmentProxyClient() {
        for (String envName : List.of("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy", "ALL_PROXY", "all_proxy")) {
            HttpClient client = buildProxyHttpClient(parseProxyAddress(System.getenv(envName)));
            if (client != null) {
                return client;
            }
        }
        return null;
    }

    private HttpClient buildCommonLocalProxyClient() {
        for (Integer port : COMMON_LOCAL_PROXY_PORTS) {
            if (port == null || port <= 0 || !isLocalPortOpen(port)) {
                continue;
            }
            return buildProxyHttpClient("127.0.0.1", port);
        }
        return null;
    }

    private HttpClient buildProxyHttpClient(ProxyAddress address) {
        return address == null ? null : buildProxyHttpClient(address.host(), address.port());
    }

    private HttpClient buildProxyHttpClient(String host, Integer port) {
        if (!StringUtils.hasText(host) || port == null || port <= 0) {
            return null;
        }
        return buildHttpClient(ProxySelector.of(new InetSocketAddress(host, port)));
    }

    private ProxyAddress parseProxyAddress(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            String normalized = rawValue.contains("://") ? rawValue : "http://" + rawValue;
            URI uri = URI.create(normalized);
            if (!StringUtils.hasText(uri.getHost()) || uri.getPort() <= 0) {
                return null;
            }
            return new ProxyAddress(uri.getHost(), uri.getPort());
        } catch (Exception ex) {
            log.warn("Skip invalid proxy address {}: {}", rawValue, ex.getMessage());
            return null;
        }
    }

    private boolean isLocalPortOpen(int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private HttpClient buildHttpClient(ProxySelector proxySelector) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1);
        if (proxySelector != null) {
            builder.proxy(proxySelector);
        }
        return builder.build();
    }

    private static final class DirectProxySelector extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
        }
    }

    private record MediaClientCandidate(String name, HttpClient client) {
    }

    private record ProxyAddress(String host, int port) {
    }
}
