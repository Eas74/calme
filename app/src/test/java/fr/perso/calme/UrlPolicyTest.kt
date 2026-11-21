package fr.perso.calme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test fun allowsInstagramAndCdn() {
        assertTrue(UrlPolicy.isRequestAllowed("https://www.instagram.com/"))
        assertTrue(UrlPolicy.isRequestAllowed("https://scontent-cdg4-1.cdninstagram.com/v/x.jpg"))
        assertTrue(UrlPolicy.isRequestAllowed("https://static.xx.fbcdn.net/rsrc.php/x.js"))
        assertTrue(UrlPolicy.isRequestAllowed("blob:https://www.instagram.com/abc"))
    }

    @Test fun blocksLookalikesAndThirdParties() {
        assertFalse(UrlPolicy.isRequestAllowed("https://instagram.com.evil.com/"))
        assertFalse(UrlPolicy.isRequestAllowed("https://notinstagram.com/"))
        assertFalse(UrlPolicy.isRequestAllowed("https://www.facebook.com/tr?id=1"))
        assertFalse(UrlPolicy.isRequestAllowed("http://www.instagram.com/"))
        assertFalse(UrlPolicy.isRequestAllowed("javascript:alert(1)"))
        assertFalse(UrlPolicy.isRequestAllowed("file:///data/data/x"))
        assertFalse(UrlPolicy.isRequestAllowed("not a url"))
    }

    @Test fun blocksExploreAndReelsPagesOnly() {
        assertFalse(UrlPolicy.isPageAllowed("https://www.instagram.com/explore/"))
        assertFalse(UrlPolicy.isPageAllowed("https://www.instagram.com/explore/tags/cat/"))
        assertFalse(UrlPolicy.isPageAllowed("https://www.instagram.com/reels/"))
        assertFalse(UrlPolicy.isPageAllowed("https://www.instagram.com/reel/AbC123/"))
        assertFalse(UrlPolicy.isPageAllowed("https://www.instagram.com/a/../explore/"))
        assertTrue(UrlPolicy.isPageAllowed("https://www.instagram.com/p/AbC123/"))
        assertTrue(UrlPolicy.isPageAllowed("https://www.instagram.com/explorer_fan/"))
        assertTrue(UrlPolicy.isPageAllowed("https://www.instagram.com/direct/inbox/"))
    }

    @Test fun scrollLockRules() {
        assertTrue(UrlPolicy.isScrollLocked("https://www.instagram.com/"))
        assertTrue(UrlPolicy.isScrollLocked("https://www.instagram.com/stories/bob/123/"))
        assertFalse(UrlPolicy.isScrollLocked("https://www.instagram.com/direct/t/456/"))
        assertFalse(UrlPolicy.isScrollLocked("https://www.instagram.com/accounts/login/"))
    }

    @Test fun detectsUnfilteredHome() {
        assertTrue(UrlPolicy.isUnfilteredHome("https://www.instagram.com/"))
        assertTrue(UrlPolicy.isUnfilteredHome("https://www.instagram.com"))
        assertFalse(UrlPolicy.isUnfilteredHome(UrlPolicy.HOME_FOLLOWING))
        assertFalse(UrlPolicy.isUnfilteredHome("https://www.instagram.com/p/AbC/"))
    }
}
