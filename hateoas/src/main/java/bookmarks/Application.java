package bookmarks;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.VndErrors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
//import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

    @Bean
    CommandLineRunner init(final AccountRepository accountRepository, final BookmarkRepository bookmarkRepository) {
        return new CommandLineRunner() {
            @Override
            public void run(String... strings) throws Exception {
                List<String> names = Arrays.asList("jhoeller,dsyer,pwebb,ogierke,rwinch,mfisher,mpollack,jlong".split(","));
                for(String accountName : names)
                {
                    Account account = accountRepository.save(new Account(accountName, "password"));
                    bookmarkRepository.save(new Bookmark(account, "http://bookmark.com/1/" + accountName, "A description"));
                    bookmarkRepository.save(new Bookmark(account, "http://bookmark.com/2/" + accountName, "A description"));
                }
            }
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

class BookmarkResource extends ResourceSupport {

    private final Bookmark bookmark;

    public BookmarkResource(Bookmark bookmark) {
        String username = bookmark.getAccount().getUsername();
        this.bookmark = bookmark;
        this.add(new Link(bookmark.getUri(), "bookmark-uri"));
        this.add(linkTo(BookmarkRestController.class, username).withRel("bookmarks"));
        this.add(linkTo(methodOn(BookmarkRestController.class, username).readBookmark(username, bookmark.getId())).withSelfRel());
    }

    public Bookmark getBookmark() {
        return bookmark;
    }
}

@RestController
@RequestMapping("/{userId}/bookmarks")
class BookmarkRestController {

    private final BookmarkRepository bookmarkRepository;

    private final AccountRepository accountRepository;

    @RequestMapping(method = RequestMethod.POST)
    ResponseEntity<?> add(@PathVariable String userId, @RequestBody Bookmark input) {

        this.validateUser(userId);
        Account account = accountRepository.findByUsername(userId);

//        return accountRepository.findByUsername(userId)
//                .map(account -> {
                            Bookmark bookmark = bookmarkRepository.save(new Bookmark(account, input.uri, input.description));

                            HttpHeaders httpHeaders = new HttpHeaders();

                            Link forOneBookmark = new BookmarkResource(bookmark).getLink("self");
                            httpHeaders.setLocation(URI.create(forOneBookmark.getHref()));

                            return new ResponseEntity<>(null, httpHeaders, HttpStatus.CREATED);
//                        }
//                ).get();
    }

    @RequestMapping(value = "/{bookmarkId}", method = RequestMethod.GET)
    BookmarkResource readBookmark(@PathVariable String userId, @PathVariable Long bookmarkId) {
        this.validateUser(userId);
        return new BookmarkResource(this.bookmarkRepository.findOne(bookmarkId));
    }


    @RequestMapping(method = RequestMethod.GET)
    Resources<BookmarkResource> readBookmarks(@PathVariable String userId) {

        this.validateUser(userId);

        List<BookmarkResource> bookmarkResourceList = new ArrayList(bookmarkRepository.findByAccountUsername(userId));
//                .stream()
//                .map(BookmarkResource::new)
//                .collect(Collectors.toList());
        return new Resources<BookmarkResource>(bookmarkResourceList);
    }

    @Autowired
    BookmarkRestController(BookmarkRepository bookmarkRepository,
                           AccountRepository accountRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.accountRepository = accountRepository;
    }

    private void validateUser(String userId) {
        if(this.accountRepository.findByUsername(userId) == null) {
            throw new UserNotFoundException(userId);
        }
    }
}

@ControllerAdvice
class BookmarkControllerAdvice {

    @ResponseBody
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    VndErrors userNotFoundExceptionHandler(UserNotFoundException ex) {
        return new VndErrors("error", ex.getMessage());
    }
}


class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("could not find user '" + userId + "'.");
    }
}
