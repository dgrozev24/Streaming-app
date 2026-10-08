package com.StreamingApp.StreamingBE.audiobooks;

import com.StreamingApp.StreamingBE.accounts.AccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AudiobookService {
    public record ChapterView(int number, String title, double durationSeconds) {}
    public record BookView(String id, String title, String author, String narrator, String description,
                           String coverUrl, double durationSeconds, List<ChapterView> chapters) {}
    public record ProgressView(int chapterNumber, double positionSeconds, Instant updatedAt) {}
    private final AudiobookRepository books;
    private final ListeningProgressRepository progress;
    private final AccountRepository accounts;

    public AudiobookService(AudiobookRepository books, ListeningProgressRepository progress, AccountRepository accounts) {
        this.books = books;
        this.progress = progress;
        this.accounts = accounts;
    }

    private Audiobook book(String id) {
        return books.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audiobook not found"));
    }
    private BookView view(Audiobook book) {
        var chapters = IntStream.range(0, book.getChapters().size()).mapToObj(index -> {
            var chapter = book.getChapters().get(index);
            return new ChapterView(index + 1, chapter.getTitle(), chapter.getDurationSeconds());
        }).toList();
        return new BookView(book.getId(), book.getTitle(), book.getAuthor(), book.getNarrator(),
                book.getDescription(), book.getCoverUrl(), chapters.stream().mapToDouble(ChapterView::durationSeconds).sum(), chapters);
    }
    public List<BookView> list() {
        return books.findAll(Sort.by("title")).stream().map(this::view).toList();
    }
    public BookView detail(String id) { return view(book(id)); }
    public Audiobook.Chapter chapter(String id, int number) {
        var chapters = book(id).getChapters();
        if (number < 1 || number > chapters.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapter not found");
        return chapters.get(number - 1);
    }
    public ProgressView progress(Long accountId, String id) {
        var book = book(id);
        return progress.findByAccountIdAndBookId(accountId, id).map(saved -> {
            int number = Math.min(saved.getChapterNumber(), book.getChapters().size());
            double position = Math.min(saved.getPositionSeconds(), book.getChapters().get(number - 1).getDurationSeconds());
            return new ProgressView(number, position, saved.getUpdatedAt());
        }).orElse(new ProgressView(1, 0, null));
    }
    @Transactional
    public ProgressView saveProgress(Long accountId, String id, int number, double position) {
        var book = book(id);
        if (number < 1 || number > book.getChapters().size() || !Double.isFinite(position)
                || position < 0 || position > book.getChapters().get(number - 1).getDurationSeconds() + 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid listening position");
        }
        // Serializes first-save creation as well as later updates for the account.
        var account = accounts.findForUpdate(accountId).orElseThrow();
        var saved = progress.findByAccountIdAndBookId(accountId, id).orElseGet(ListeningProgress::new);
        saved.setAccount(account);
        saved.setBook(book);
        saved.setChapterNumber(number);
        saved.setPositionSeconds(Math.min(position, book.getChapters().get(number - 1).getDurationSeconds()));
        saved.setUpdatedAt(Instant.now());
        progress.save(saved);
        return new ProgressView(number, saved.getPositionSeconds(), saved.getUpdatedAt());
    }
}
