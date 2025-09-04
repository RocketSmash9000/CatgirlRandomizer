package com.github.RocketSmash9000;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Alert;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.embed.swing.SwingFXUtils;
import java.awt.Taskbar;
import java.awt.Toolkit;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

public class Main extends Application {

    private static final double DEFAULT_WIDTH = 900;
    private static final double DEFAULT_HEIGHT = 700;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Simple API: random image each request
    private static final String IMAGE_API = "https://nekos.moe/api/v1/random/image";

    static boolean nsfw = false;

    private final ImageView imageView = new ImageView();
    private final Label status = new Label("Ready");
    private final ObjectMapper mapper = new ObjectMapper();
    private Stage stageRef;
    private final MenuBar menuBar = new MenuBar();
    private String currentImageName = "catgirl.png";
    private volatile String currentImageId = null;
    private volatile String currentArtist = null;
    private volatile List<String> currentTags = new ArrayList<>();
    private volatile int currentLikes = -1;

    @Override
    public void start(Stage stage) {
        this.stageRef = stage;
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        BorderPane root = new BorderPane();
        root.setCenter(imageView);
        root.setStyle("-fx-background-color: black;");

        // Top menu bar
        Menu file = new Menu("File");
        MenuItem saveItem = new MenuItem("Save Image...");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        saveItem.setOnAction(e -> saveCurrentImageAs());
        file.getItems().addAll(saveItem);

        Menu actions = new Menu("Actions");
        MenuItem refreshItem = new MenuItem("Refresh");
        refreshItem.setAccelerator(new KeyCodeCombination(KeyCode.F5, KeyCombination.SHORTCUT_ANY));
        refreshItem.setOnAction(e -> loadImageAsync());

        MenuItem toggleNsfw = new MenuItem("NSFW: " + (nsfw ? "On" : "Off"));
        toggleNsfw.setOnAction(e -> {
            nsfw = !nsfw;
            toggleNsfw.setText("NSFW: " + (nsfw ? "On" : "Off"));
            loadImageAsync();
        });

        MenuItem openInBrowser = new MenuItem("Open in Browser");
        openInBrowser.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        openInBrowser.setOnAction(e -> openPostInBrowser());

        MenuItem showInfo = new MenuItem("Show Info...");
        showInfo.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN));
        showInfo.setOnAction(e -> showImageInfo());

        actions.getItems().addAll(refreshItem, toggleNsfw, openInBrowser, showInfo);
        menuBar.getMenus().addAll(file, actions);
        root.setTop(menuBar);

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        stage.setTitle("Catgirl Randomizer");
        stage.setScene(scene);
        // Set app/window icon depending on OS
        setAppIcon(stage);
        stage.show();

        loadImageAsync();
    }

    @SuppressWarnings("D")
    private void updateInfoFromRandomJson(JsonNode jsonRoot) {
        try {
            JsonNode first = jsonRoot.at("/images/0");
            if (!first.isMissingNode()) {
                String artist = optionalText(first.get("artist")).orElse(null);
                List<String> tags = new ArrayList<>();
                JsonNode tagsNode = first.get("tags");
                if (tagsNode != null && tagsNode.isArray()) {
                    for (JsonNode t : tagsNode) {
                        String v = t.asText(null);
                        if (v != null && !v.isBlank()) tags.add(v);
                    }
                }
                int likes = first.has("likes") ? first.get("likes").asInt(-1) : -1;
                currentArtist = artist;
                currentTags = tags;
                currentLikes = likes;
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("D")
    private void updateInfoFromDetails(JsonNode details) {
        try {
            JsonNode imageNode = details.get("image");
            if (imageNode != null && imageNode.isObject()) {
                String artist = optionalText(imageNode.get("artist")).orElse(currentArtist);
                List<String> tags = new ArrayList<>();
                JsonNode tagsNode = imageNode.get("tags");
                if (tagsNode != null && tagsNode.isArray()) {
                    for (JsonNode t : tagsNode) {
                        String v = t.asText(null);
                        if (v != null && !v.isBlank()) tags.add(v);
                    }
                } else {
                    tags = currentTags;
                }
                int likes = imageNode.has("likes") ? imageNode.get("likes").asInt(currentLikes) : currentLikes;
                currentArtist = artist;
                currentTags = tags;
                currentLikes = likes;
            }
        } catch (Exception ignored) {
        }
    }

    private void showImageInfo() {
        String artist = (currentArtist != null && !currentArtist.isBlank()) ? currentArtist : "Unknown";
        List<String> tagsList = (currentTags != null) ? currentTags : List.of();
        String likes = (currentLikes >= 0) ? Integer.toString(currentLikes) : "Unknown";

        // Build styled UI content
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 12;");

        HBox artistRow = new HBox(8);
        Label artistLbl = new Label("Artist:");
        artistLbl.setStyle("-fx-font-weight: bold;");
        Label artistVal = new Label(artist);
        artistRow.getChildren().addAll(artistLbl, artistVal);

        HBox likesRow = new HBox(8);
        Label likesLbl = new Label("Likes:");
        likesLbl.setStyle("-fx-font-weight: bold;");
        Label likesVal = new Label(likes);
        likesRow.getChildren().addAll(likesLbl, likesVal);

        // Tags as pill chips in a collapsible section
        FlowPane tagsPane = new FlowPane();
        tagsPane.setHgap(6);
        tagsPane.setVgap(6);
        tagsPane.setPrefWrapLength(360); // allow wrapping
        if (tagsList.isEmpty()) {
            Label none = new Label("None");
            none.setStyle("-fx-opacity: 0.8;");
            tagsPane.getChildren().add(none);
        } else {
            for (String t : tagsList) {
                if (t == null || t.isBlank()) continue;
                Label chip = new Label(t);
                chip.setStyle(
                        "-fx-background-color: #2b2f3a;" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 4 8;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: #4a4f5c;" +
                        "-fx-border-radius: 10;" +
                        "-fx-font-size: 12;"
                );
                tagsPane.getChildren().add(chip);
            }
        }
        TitledPane tagsSection = new TitledPane("Tags (" + tagsList.size() + ")", tagsPane);
        tagsSection.setExpanded(false); // collapsible

        content.getChildren().addAll(artistRow, likesRow, tagsSection);

        // Wrap content in a scroll pane so the dialog stays usable with many tags
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Let tag chips wrap responsively to the dialog width
        tagsPane.prefWrapLengthProperty().bind(scroll.widthProperty().subtract(32));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Image Info");
        alert.setHeaderText("Details");
        alert.getDialogPane().setContent(scroll);
        alert.getDialogPane().setPrefSize(460, 420);
        alert.getDialogPane().setMinHeight(Region.USE_COMPUTED_SIZE);
        alert.setResizable(true);
        alert.initOwner(stageRef);
        alert.showAndWait();
    }

    private String parseIdFromUrl(String url) {
        if (url == null) return null;
        // Accept forms like .../image/<id> or .../post/<id>
        int idx = url.indexOf("/image/");
        if (idx < 0) idx = url.indexOf("/post/");
        if (idx >= 0) {
            String rest = url.substring(idx + 7); // skip '/image/' or '/post/' (both 7 chars)
            int end = rest.indexOf('/') >= 0 ? rest.indexOf('/') : rest.length();
            String id = rest.substring(0, end);
            return id.isBlank() ? null : id;
        }
        return null;
    }

    private void openPostInBrowser() {
        String id = currentImageId;
        if ((id == null || id.isBlank()) && imageView.getImage() != null) {
            String url = imageView.getImage().getUrl();
            id = parseIdFromUrl(url);
        }
        if (id == null || id.isBlank()) {
            setStatus("No image ID available to open");
            return;
        }
        String postUrl = "https://nekos.moe/post/" + id;
        try {
            getHostServices().showDocument(postUrl);
            setStatus("Opened: " + postUrl);
        } catch (Exception ex) {
            setStatus("Open failed: " + ex.getMessage());
        }
    }

    private void saveCurrentImageAs() {
        Image img = imageView.getImage();
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
            setStatus("No image to save");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Image");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Image (*.png)", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Image (*.jpg, *.jpeg)", "*.jpg", "*.jpeg")
        );
        // Suggest a default file name
        if (currentImageName != null && !currentImageName.isBlank()) {
            fc.setInitialFileName(currentImageName);
        } else {
            fc.setInitialFileName("catgirl.png");
        }

        java.io.File file = fc.showSaveDialog(stageRef);
        if (file == null) return; // user cancelled

        // Determine format from extension
        String name = file.getName().toLowerCase();
        String format = (name.endsWith(".jpg") || name.endsWith(".jpeg")) ? "jpg" : "png";

        try {
            var bimg = SwingFXUtils.fromFXImage(img, null);
            boolean ok = ImageIO.write(bimg, format, file);
            if (ok) {
                setStatus("Saved to " + file.getAbsolutePath());
            } else {
                setStatus("Failed to save: unsupported format");
            }
        } catch (Exception ex) {
            setStatus("Save error: " + ex.getMessage());
        }
    }

    @SuppressWarnings("D")
    private void loadImageAsync() {
        setStatus("Loading...");
        currentImageId = null; // reset until known
        currentArtist = null;
        currentTags = new ArrayList<>();
        currentLikes = -1;
        // Run network on a background thread
        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(IMAGE_API + "?nsfw=" + nsfw))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();
                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    Platform.runLater(() -> setStatus("HTTP " + resp.statusCode()));
                    return;
                }

                String contentType = resp.headers().firstValue("Content-Type").orElse("");
                byte[] body = resp.body();

                if (contentType.contains("image/")) {
                    // Direct image bytes
                    showImageBytes(body);
                    return;
                }

                if (contentType.contains("application/json") || looksLikeJson(body)) {
                    String json = new String(body, StandardCharsets.UTF_8);
                    JsonNode root = mapper.readTree(json);
                    // Try to find a usable URL in a flexible way
                    Optional<String> imageUrl = extractImageUrl(root);
                    // Try to capture an ID if present even when URL is present
                    Optional<String> id = optionalText(root.at("/images/0/id"))
                            .or(() -> optionalText(root.at("/images/0/_id")));
                    id.ifPresent(v -> currentImageId = v);
                    // Extract informational fields if present
                    updateInfoFromRandomJson(root);
                    if (imageUrl.isPresent()) {
                        fetchAndShowImage(imageUrl.get());
                        return;
                    }
                    // Try id -> construct plausible URL
                    if (id.isPresent()) {
                        currentImageId = id.get();
                        // Per observed API behavior, loading the public image route works
                        String url = "https://nekos.moe/image/" + id.get();
                        showImageFromUrl(url);
                        return;
                    }
                    Platform.runLater(() -> setStatus("JSON parsed but no image URL/id found"));
                    return;
                }

                Platform.runLater(() -> setStatus("Unsupported Content-Type: " + contentType));
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Error: " + ex.getMessage()));
            }
        }, "image-loader");
        t.setDaemon(true);
        t.start();
    }

    private boolean looksLikeJson(byte[] body) {
        if (body == null || body.length == 0) return false;
        byte first = body[0];
        return first == '{' || first == '[';
    }

    private Optional<String> optionalText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return Optional.empty();
        String text = node.asText(null);
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    @SuppressWarnings("D")
    private Optional<String> extractImageUrl(JsonNode root) {
        // Try common fields under images[0]
        JsonNode first = root.at("/images/0");
        if (first.isMissingNode()) return Optional.empty();

        // Direct URL fields
        for (String key : new String[]{"url", "image_url", "file", "path"}) {
            Optional<String> v = optionalText(first.get(key));
            if (v.isPresent() && (v.get().startsWith("http://") || v.get().startsWith("https://"))) {
                return v;
            }
        }
        // Sometimes nested formats
        JsonNode formats = first.get("formats");
        if (formats != null && formats.isObject()) {
            for (JsonNode fmt : formats) {
                Optional<String> v = optionalText(fmt.get("url"));
                if (v.isPresent() && (v.get().startsWith("http://") || v.get().startsWith("https://"))) {
                    return v;
                }
            }
        }
        return Optional.empty();
    }

    private void fetchAndShowImage(String url) {
        // Use JavaFX's built-in URL loading to avoid content-type quirks
        showImageFromUrl(url);
    }

    @SuppressWarnings("D")
    private void showImageFromUrl(String url) {
        setStatus("Loading from URL: " + url);
        Platform.runLater(() -> {
            Image image = new Image(url, true);
            image.errorProperty().addListener((obs, wasErr, isErr) -> {
                if (isErr) {
                    setStatus("Image load error: " + (image.getException() != null ? image.getException().getMessage() : "unknown"));
                }
            });
            // Infer a reasonable default file name from URL
            if (!url.isBlank()) {
                String name = url;
                int q = name.indexOf('?');
                if (q >= 0) name = name.substring(0, q);
                int slash = name.lastIndexOf('/');
                if (slash >= 0 && slash < name.length() - 1) name = name.substring(slash + 1);
                if (name.isBlank()) name = "catgirl.png";
                // ensure an extension
                if (!name.contains(".")) name = name + ".png";
                currentImageName = name;
                // attempt to extract id from URL
                String maybeId = parseIdFromUrl(url);
                if (maybeId != null && !maybeId.isBlank()) currentImageId = maybeId;
            }
            // One-shot resize when dimensions are available
            final boolean[] done = {false};
            var widthL = new javafx.beans.value.ChangeListener<Number>() {
                @Override public void changed(javafx.beans.value.ObservableValue<? extends Number> o, Number ov, Number nv) {
                    if (!done[0] && nv != null && nv.doubleValue() > 0) {
                        done[0] = true;
                        setStatus("Image loaded from URL");
                        resizeWindowToImage(image, menuBar.getHeight());
                        // detach
                        image.widthProperty().removeListener(this);
                    }
                }
            };
            image.widthProperty().addListener(widthL);
            image.progressProperty().addListener((obs, oldV, newV) -> {
                if (!done[0] && newV != null && newV.doubleValue() >= 1.0 && image.getWidth() > 0) {
                    done[0] = true;
                    setStatus("Image loaded from URL");
                    resizeWindowToImage(image, menuBar.getHeight());
                }
            });
            imageView.setImage(image);
        });
    }

    private void fetchFromDetailsById(String id) {
        setStatus("Resolving details for id: " + id);
        try {
            String detailsUrl = "https://nekos.moe/api/v1/image/" + id;
            HttpRequest infoReq = HttpRequest.newBuilder()
                    .uri(URI.create(detailsUrl))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            http.sendAsync(infoReq, HttpResponse.BodyHandlers.ofByteArray())
                    .whenComplete((resp, err) -> {
                        if (err != null) {
                            Platform.runLater(() -> setStatus("Details fetch error: " + err.getMessage()));
                            return;
                        }
                        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                            Platform.runLater(() -> setStatus("Details HTTP " + resp.statusCode()));
                            return;
                        }
                        byte[] body = resp.body();
                        try {
                            JsonNode details = mapper.readTree(new String(body, StandardCharsets.UTF_8));
                            Optional<String> url = extractImageUrl(details)
                                    .or(() -> optionalText(details.at("/image/url")))
                                    .or(() -> optionalText(details.at("/image/image_url")))
                                    .or(() -> optionalText(details.at("/image/file")))
                                    .or(() -> optionalText(details.at("/image/path")));
                            if (url.isPresent()) {
                                String u = url.get();
                                if (u.startsWith("/")) {
                                    u = "https://nekos.moe" + u;
                                }
                                // Also attempt to extract info from details shape
                                updateInfoFromDetails(details);
                                showImageFromUrl(u);
                                return;
                            }
                            showImageFromUrl("https://nekos.moe/image/" + id);
                        } catch (Exception ex) {
                            Platform.runLater(() -> setStatus("Details parse error: " + ex.getMessage()));
                        }
                    });
        } catch (Exception e) {
            Platform.runLater(() -> setStatus("Bad details URL: " + e.getMessage()));
        }
    }

    private void showImageBytes(byte[] bytes) {
        Image image = new Image(new ByteArrayInputStream(bytes));
        Platform.runLater(() -> {
            imageView.setImage(image);
            setStatus("Loaded " + bytes.length + " bytes");
            currentImageName = "catgirl.png"; // fallback name when not from URL
            currentImageId = null;
            currentArtist = null;
            currentTags = new ArrayList<>();
            currentLikes = -1;
            double topBar = stageRef != null && ((BorderPane) stageRef.getScene().getRoot()).getTop() != null
                    ? ((BorderPane) stageRef.getScene().getRoot()).getTop().getBoundsInParent().getHeight()
                    : 0;
            resizeWindowToImage(image, topBar);
        });
    }

    private void setStatus(String text) {
        if (Platform.isFxApplicationThread()) {
            status.setText(text);
            if (stageRef != null) {
                stageRef.setTitle("Catgirl Randomizer — " + text);
            }
        } else {
            Platform.runLater(() -> {
                status.setText(text);
                if (stageRef != null) {
                    stageRef.setTitle("Catgirl Randomizer — " + text);
                }
            });
        }
    }

    private void resizeWindowToImage(Image img, double extraTop) {
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0 || stageRef == null) return;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> resizeWindowToImage(img, extraTop));
            return;
        }

        var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        double imgW = img.getWidth();
        double topBarH = extraTop;
        BorderPane root = (BorderPane) stageRef.getScene().getRoot();
        if (topBarH <= 0 && root.getTop() != null) {
            // Fallback to preferred height if current height is not yet measured
            topBarH = Math.max(0, root.getTop().prefHeight(-1));
        }
        double imgHWithBars = img.getHeight() + topBarH; // include menu bar height

        // If image would exceed screen, scale image to the default content area and size window to the scaled image
        if (imgW > screen.getWidth() || imgHWithBars > screen.getHeight()) {
            double contentMaxH = Math.max(0, DEFAULT_HEIGHT - topBarH);
            double scale = Math.min(DEFAULT_WIDTH / imgW, contentMaxH / (imgHWithBars - topBarH));
            scale = Math.max(0.01, Math.min(1.0, scale));
            double windowW = Math.floor(imgW * scale);
            double scaledH = Math.floor((imgHWithBars - topBarH) * scale);

            imageView.setPreserveRatio(true);
            imageView.setFitWidth(windowW);
            imageView.setFitHeight(scaledH);

            double windowH = scaledH + topBarH;

            root.setPrefSize(windowW, windowH);
            root.applyCss();
            root.layout();

            double decoW = Math.max(0, stageRef.getWidth() - stageRef.getScene().getWidth());
            double decoH = Math.max(0, stageRef.getHeight() - stageRef.getScene().getHeight());
            double winW = Math.min(screen.getWidth(), windowW + decoW);
            double winH = Math.min(screen.getHeight(), windowH + decoH);

            double oldMinW = stageRef.getMinWidth();
            double oldMinH = stageRef.getMinHeight();
            stageRef.setMinWidth(0);
            stageRef.setMinHeight(0);
            stageRef.setWidth(winW);
            stageRef.setHeight(winH);
            stageRef.setMinWidth(oldMinW);
            stageRef.setMinHeight(oldMinH);
            stageRef.centerOnScreen();
            return;
        }

        // Otherwise, size window to the image (clamped to screen)
        double targetW = Math.min(imgW, screen.getWidth());
        double targetH = Math.min(imgHWithBars, screen.getHeight());

        // Let ImageView report image intrinsic size (no fit constraints)
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(0);
        imageView.setFitHeight(0);

        root.setPrefSize(targetW, targetH);
        // Ensure layout is up-to-date before sizing the stage
        root.applyCss();
        root.layout();

        // Compute window decoration deltas to set exact window size
        double decoW = Math.max(0, stageRef.getWidth() - stageRef.getScene().getWidth());
        double decoH = Math.max(0, stageRef.getHeight() - stageRef.getScene().getHeight());
        double winW = Math.min(screen.getWidth(), targetW + decoW);
        double winH = Math.min(screen.getHeight(), targetH + decoH);

        // Allow shrinking below any previous min size
        double oldMinW = stageRef.getMinWidth();
        double oldMinH = stageRef.getMinHeight();
        stageRef.setMinWidth(0);
        stageRef.setMinHeight(0);
        stageRef.setWidth(winW);
        stageRef.setHeight(winH);
        // Restore min sizes
        stageRef.setMinWidth(oldMinW);
        stageRef.setMinHeight(oldMinH);
        stageRef.centerOnScreen();
    }

    private void setAppIcon(Stage stage) {
        if (stage == null) return;
        String os = System.getProperty("os.name", "").toLowerCase();
        // Prefer ICO on Windows if provided, else PNG. On other OSes use PNG.
        try {
            if (os.contains("win")) {
                var icoUrl = getClass().getResource("/icon.ico");
                if (icoUrl != null) {
                    try {
                        // JavaFX Image may not support ICO everywhere; try anyway.
                        Image fxIco = new Image(icoUrl.toExternalForm());
                        if (!fxIco.isError()) {
                            stage.getIcons().add(fxIco);
                        }
                    } catch (Exception ignored) { }
                }
            }
            // Always add PNG as a reliable fallback/default
            var pngUrl = getClass().getResource("/icon.png");
            if (pngUrl != null) {
                try {
                    Image png = new Image(pngUrl.toExternalForm());
                    if (!png.isError()) {
                        stage.getIcons().add(png);
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }

        // Also set OS taskbar/dock icon where supported via AWT
        try {
            Taskbar tb = Taskbar.getTaskbar();
            if (tb.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                java.awt.Image awtImg = null;
                if (os.contains("win")) {
                    var icoUrl = getClass().getResource("/icon.ico");
                    if (icoUrl != null) {
                        try {
                            awtImg = Toolkit.getDefaultToolkit().getImage(icoUrl);
                        } catch (Exception ignored) { }
                    }
                }
                if (awtImg == null) {
                    var pngUrl = getClass().getResource("/icon.png");
                    if (pngUrl != null) {
                        try {
                            awtImg = Toolkit.getDefaultToolkit().getImage(pngUrl);
                        } catch (Exception ignored) { }
                    }
                }
                if (awtImg != null) {
                    tb.setIconImage(awtImg);
                }
            }
        } catch (Throwable ignored) { }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
