package com.imzhizi.breeze.devtools.uri;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BreezeJumpUriParserTest {
    @Test
    void parsesClassOnlyUri() {
        BreezeJumpTarget target = BreezeJumpUriParser.parse("breeze-jump://com.foo.Bar");
        assertNotNull(target);
        assertEquals("com.foo.Bar", target.getClassFqn());
        assertNull(target.getMemberName());
        assertNull(target.getLineNumber());
    }

    @Test
    void parsesMethodUri() {
        BreezeJumpTarget target = BreezeJumpUriParser.parse("breeze-jump://com.foo.Bar#baz");
        assertNotNull(target);
        assertEquals("com.foo.Bar", target.getClassFqn());
        assertEquals("baz", target.getNormalizedMemberName());
        assertNull(target.getLineNumber());
    }

    @Test
    void parsesLineUri() {
        BreezeJumpTarget target = BreezeJumpUriParser.parse("breeze-jump://com.foo.Bar:123");
        assertNotNull(target);
        assertEquals("com.foo.Bar", target.getClassFqn());
        assertNull(target.getMemberName());
        assertEquals(123, target.getLineNumber());
    }

    @Test
    void parsesMethodAndLineUri() {
        BreezeJumpTarget target = BreezeJumpUriParser.parse("breeze-jump://com.foo.Bar#baz:45");
        assertNotNull(target);
        assertEquals("com.foo.Bar", target.getClassFqn());
        assertEquals("baz", target.getNormalizedMemberName());
        assertEquals(45, target.getLineNumber());
    }

    @Test
    void rejectsInvalidUri() {
        assertNull(BreezeJumpUriParser.parse("https://example.com"));
        assertNull(BreezeJumpUriParser.parse("breeze-jump://not valid"));
    }
}