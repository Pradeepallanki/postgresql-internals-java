package com.pradeep.dbdemo.storage;

public record RID(int pageId, short slotNumber) {
    // RID is what systems like Postgres are using to identify a tuple ( a row).
    // postgres system will never point to a tuple, because it creates complexities during updates, delete. Lets say a row is taking 60 bytes today, after update it may consume 120 bytes, how will you allocate that space in the page?
    // Or lets say somebody deletes a row, how will you delete it? what happens to the space afterwards? shifting the entries to and pro in a page, will create performance issues and wastes space ( which compaction may claim later, but compaction again is a complex process).
    // So instead of saying Tuple starts at byte 8012, we just say Page 7, slot 2.
    // If at all you need to update a row, just update the row and change the offset number in the slot. that's it, indexes stay as it is, your record entry stays as it is. safely you can allocate more memory to the newly updated row without having to run into bigger complexities by just updating the offset value.

}
