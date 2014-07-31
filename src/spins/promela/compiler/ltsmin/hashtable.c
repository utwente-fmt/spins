#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <assert.h>
#include <string.h>
#include <errno.h>

#undef get16bits
#if (defined(__GNUC__) && defined(__i386__)) || defined(__WATCOMC__) \
  || defined(_MSC_VER) || defined (__BORLANDC__) || defined (__TURBOC__)
#define get16bits(d) (*((const uint16_t *) (d)))
#endif

#if !defined (get16bits)
#define get16bits(d) ((((uint32_t)(((const uint8_t *)(d))[1])) << 8)\
                       +(uint32_t)(((const uint8_t *)(d))[0]) )
#endif

uint32_t
spins_hash (const void *data_, int len, uint32_t hash)
{
    const unsigned char *data = data_;
    uint32_t tmp;
    int rem;

    if (len <= 0 || data == NULL) return 0;

    rem = len & 3;
    len >>= 2;

    /* Main loop */
    for (;len > 0; len--) {
        hash  += get16bits (data);
        tmp    = (get16bits (data+2) << 11) ^ hash;
        hash   = (hash << 16) ^ tmp;
        data  += 2*sizeof (uint16_t);
        hash  += hash >> 11;
    }

    /* Handle end cases */
    switch (rem) {
        case 3: hash += get16bits (data);
                hash ^= hash << 16;
                hash ^= data[sizeof (uint16_t)] << 18;
                hash += hash >> 11;
                break;
        case 2: hash += get16bits (data);
                hash ^= hash << 11;
                hash += hash >> 17;
                break;
        case 1: hash += *data;
                hash ^= hash << 10;
                hash += hash >> 1;
    }

    /* Force "avalanching" of final 127 bits */
    hash ^= hash << 3;
    hash += hash >> 5;
    hash ^= hash << 4;
    hash += hash >> 17;
    hash ^= hash << 25;
    hash += hash >> 6;

    return hash;
}

void *
spins_align (size_t align, size_t size)
{
    void *ret;
    errno = posix_memalign (&ret, align, size);
    if (errno) {
    switch (errno) {
        case ENOMEM:
            printf ("Out of memory - ");
            exit (1);
        case EINVAL:
            printf ("Invalid alignment %zu - ", align);
            exit (1);
        default:
            printf("error allocating %zu bytes aligned at %zu\n", size, align);
            exit (1);
    }}
    return ret;
}

typedef uint32_t spins_mem_hash_t;

typedef struct spins_state_db_s {
    size_t              length;
    size_t              bytes;
    size_t              size;
    size_t              size3;
    size_t              init_size;
    size_t              init_size3;
    size_t              mask;
    size_t				max;
    int                *data;
    spins_mem_hash_t         *hash;
    size_t				load;
} spins_state_db_t;

#define STATE_DB_FULL -1

#define EMPTY 0
#define CACHE_LINE 6
static const size_t SJ_CACHE_LINE_SIZE = 1 << CACHE_LINE;
static const size_t SJ_CACHE_LINE_MEM_SIZE =   (1UL<<CACHE_LINE) / sizeof (spins_mem_hash_t);
static const size_t SJ_CACHE_LINE_MEM_MASK = -((1UL<<CACHE_LINE) / sizeof (spins_mem_hash_t));
static const spins_mem_hash_t SJ_FULL = ((spins_mem_hash_t)-1) ^ (((spins_mem_hash_t)-1)>>1); // 1000
static const spins_mem_hash_t SJ_MASK = ((spins_mem_hash_t)-1)>>1;                      // 0111
static const spins_mem_hash_t SJ_TOMB = 1;                                        // 0001
static const size_t SJ_NONE = -1UL;

extern int spins_state_db_lookup_hash (spins_state_db_t *dbs, const int *v, spins_mem_hash_t *pre);

static inline spins_mem_hash_t *
spins_memoized (const spins_state_db_t *dbs, size_t ref)
{
    return &dbs->hash[ref];
}

static inline int *
spins_state (const spins_state_db_t *dbs, size_t ref)
{
    size_t              l = dbs->length;
    return &dbs->data[ref * l];
}

int
spins_resize (spins_state_db_t *dbs)
{
    if (dbs->size == (dbs->max >> 1))
        return false;
    size_t i;
    size_t size = dbs->size;
    size_t newsize = dbs->size <<= 1;
    dbs->size3 <<= 1;
    dbs->mask = dbs->size - 1;

    // collect elements at dbs->table + newsize
    int todos = 0;
    for (i = 0; i < size; i++) {
        spins_mem_hash_t h = *spins_memoized(dbs,i);
        if (EMPTY == h || (h&SJ_MASK) == i) continue;
        size_t newidx = newsize + todos;
        *spins_memoized(dbs,newidx) = h;
        memcpy(spins_state(dbs,newidx),spins_state(dbs,i),dbs->bytes);
        *spins_memoized(dbs,i) = EMPTY;
    }
    memset (dbs->hash + size, 0, sizeof (spins_mem_hash_t[size]));
    for (i = newsize; i < newsize + todos; i++) {
        spins_mem_hash_t h = *spins_memoized(dbs,i);
        spins_state_db_lookup_hash (dbs, spins_state(dbs,i), &h);
    }
    return true;
}

int
spins_state_db_lookup (spins_state_db_t *dbs, const int *v)
{
    return spins_state_db_lookup_hash (dbs, v, NULL);
}

int
spins_state_db_lookup_hash (spins_state_db_t *dbs, const int *v, spins_mem_hash_t *pre)
{
    size_t 				i;
    size_t              seed = 0;
    size_t              tomb = SJ_NONE;
    size_t              b = dbs->bytes;
    spins_mem_hash_t          h = (NULL==pre ? spins_hash ((char *)v, b, 0) : *pre);
    spins_mem_hash_t          mem = h;
    while ((dbs->load << 2) < dbs->size3) { // while >75% full
        size_t              ref = h & dbs->mask;
        size_t              line_begin = ref & SJ_CACHE_LINE_MEM_MASK;
        size_t              line_end = line_begin + SJ_CACHE_LINE_MEM_SIZE;
        for (i = 0; i < SJ_CACHE_LINE_MEM_SIZE; i++) {
            if (tomb == SJ_NONE && SJ_TOMB == *spins_memoized(dbs,ref))
                tomb = ref;
			if (EMPTY == *spins_memoized(dbs,ref)) {
			    if (tomb != SJ_NONE)
			        ref = tomb;
			    *spins_memoized(dbs,ref) = mem | SJ_FULL;
				dbs->load++;
				memcpy (spins_state(dbs,ref), v, b);
				return false;
			}
            if ( ((mem | SJ_FULL) == *spins_memoized(dbs,ref)) &&
                  0 == memcmp (spins_state(dbs,ref), v, b) ) {
                if (tomb != SJ_NONE) {
                    *spins_memoized(dbs,tomb) = mem | SJ_FULL;
                    memcpy (spins_state(dbs,tomb), v, b);
                    *spins_memoized(dbs,ref) = SJ_TOMB;
                }
				return true;
            }
            ref = (ref+1 == line_end ? line_begin : ref+1);
        }
        h = spins_hash ((char *)v, b, h + (seed++));
    }
    if (spins_resize (dbs)) {
        return spins_state_db_lookup_hash (dbs, v, &mem);
    } else {
        return STATE_DB_FULL;
    }
}

void
spins_state_db_clear (spins_state_db_t *dbs)
{
    dbs->load = 0;
    dbs->size = dbs->init_size;
    dbs->size3 = dbs->init_size3;
    dbs->mask = dbs->size - 1;
    memset (dbs->hash, 0, sizeof (spins_mem_hash_t[dbs->size]));
}

spins_state_db_t *
spins_state_db_create (size_t length, size_t init_size, size_t max_size)
{
    assert (init_size < max_size);
    spins_state_db_t           *dbs = spins_align (SJ_CACHE_LINE_SIZE, sizeof (spins_state_db_t));
    dbs->length = length;
    dbs->bytes = sizeof (int[length]);
    dbs->max = 1UL << max_size;
    dbs->data = spins_align (SJ_CACHE_LINE_SIZE, sizeof (int[dbs->max][length]));
    dbs->hash = spins_align (SJ_CACHE_LINE_SIZE, sizeof (spins_mem_hash_t[dbs->max]));
    dbs->init_size = 1UL<<init_size;
    dbs->init_size3 = dbs->init_size * 3;
    dbs->size = dbs->init_size;
    dbs->size3 = dbs->init_size3;
    dbs->mask = dbs->size - 1;
	spins_state_db_clear (dbs);
    return dbs;
}

void
spins_state_db_free (spins_state_db_t *dbs)
{
    free (dbs->data);
    free (dbs->hash);
    free (dbs);
}

void
spins_test ()
{
    spins_state_db_t *dbs = spins_state_db_create (10, 2, 10);
    int state[10];
    size_t x, i, j;
    for (x = 0; x < 500; x++)
    for (i = 0; i < 768; i++) {
        for (j = 0; j < 10; j++)
            state[j] = i;
        int seen = spins_state_db_lookup (dbs, state);
        if (seen && !x) {
            printf("seen = %d at x=%zu i=%zu load=%zu size=%zu\n", seen, x, i, dbs->load, dbs->size);
            assert (false);
        }
    }
    int seen = spins_state_db_lookup (dbs, state);
    assert (seen == STATE_DB_FULL);
    spins_state_db_free (dbs);
}
