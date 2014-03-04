
typedef bool (*check_f)(int,int);

bool
has_guard (int t, check_f check, int o)
{
    int x;
    int len = gm_trans[t][0];
    for (x = 1; x <= len; x++) {
        int guard = gm_trans[t][x];
        if (check(guard, o)) return true;
    }
    return false;
}

bool disabled_by (int guard, int o) {
    return dm_must_disable[guard][o];
}

bool nes_has (int guard, int o) {
    return nes_dm[guard][o];
}

bool nds_has (int guard, int o) {
    return nds_dm[guard][o];
}

bool not_coenabled (int guard, int o) {
    return !co_dm[guard][o];
}

bool not_coenabled_with_guard_of (int guard, int o) {
    return has_guard(o, not_coenabled, guard);
}

static int *t2g = NULL;
static int *dnb = NULL;
static int *dna = NULL;
static int *t2r = NULL;
static int *t2w = NULL;

void
spins_create_matrices ()
{
    int l = spins_get_label_count();
    int g = spins_get_guard_count();
    int t = spins_get_transition_groups();
    int s = spins_get_state_size();
    int i, j, k;

    // create t2w / t2r
    t2w = malloc (sizeof(int[t][s]));
    t2r = malloc (sizeof(int[t][s]));
    for (i = 0; i < t; i++) {
        for (j = 0; j < s; j++) {
            t2r[i * s + j] = transition_dependency[i][0][j];
            t2w[i * s + j] = transition_dependency[i][1][j];
        }
    }

    // create G_T
    t2g = malloc (sizeof(int[t][l]));
    for (i = 0; i < t; i++) {
        for (j = 0; j < l; j++) {
            t2g[i * l + j] = 0;
        }
        int len = gm_trans[i][0];
        for (j = 1; j <= len; j++) {
            int guard = gm_trans[i][j];
            t2g[i * l + guard] = 1;
        }
    }

    // DNA
    dna = malloc (sizeof(int[t][t]));
    for (i = 0; i < t; i++) {
        for (j = 0; j < t; j++) {
            dna[i * t + j] = (i == j);

            if (i == j) continue; // 1

            if (has_guard(i, not_coenabled_with_guard_of, j)) { // i and j are never coenabled
                continue;// 0
            }

            if (!dna_dm[i][j]) {
                continue; // 0
            }

            // i and j may disable eachother
            if (has_guard(i, nds_has, j) || has_guard(j, nds_has, i)) {
                dna[i * t + j] = 1;
                continue;// 0
            }

            // is even dependent? Front-end might miss it.
            for (k = 0; k < s; k++) {
                if ((transition_dependency[i][1][k] && actions_read_dependency[j][k]) ||
                    (actions_read_dependency[i][k] && transition_dependency[j][1][k]) ) {
                    dna[i * t + j] = 1;
                    break;
                }
            }
        }
    }

    // create DNB
    dnb = malloc (sizeof(int[t][t]));
    for (i = 0; i < t; i++) {
        for (j = 0; j < t; j++) {
            k = dnb[i * t + j] = dna[i * t + j];

            if (has_guard(i, disabled_by, j)) { // j must-disable a guard of i
                dnb[i * t + j] = 0;
                continue; // 0
            }

            if (has_guard(i, nes_has, j)) { // j could enable guard of i
                dnb[i * t + j] = 1;
            }
        }
    }
}

void
spins_use_query()
{
    int l = spins_get_label_count();
    int g = spins_get_guard_count();
    int t = spins_get_transition_groups();
    int s = spins_get_state_size();

    printf("Dependency inspector.\n");
    printf("Groups: %d\n", t);
    printf("Guards: %d\n", g);
    printf("Labels: %d\n", l);
    printf("Slots : %d\n", s);
    printf("\n");
    printf("The following matrix commands are implemented (with in and out dimensions):\n");
    printf("- {0 ..%4d} g {0 ..%4d}\tprint G_T (transition guard function).\n",    t-1, l-1);
    printf("- {0 ..%4d} u {0 ..%4d}\tprint COM (transition^2 commutes).\n",        t-1, t-1);
    printf("- {0 ..%4d} a {0 ..%4d}\tprint DNA (transition^2 do-not-accord).\n",   t-1, t-1);
    printf("- {0 ..%4d} A {0 ..%4d}\tprint DNA-(transition^2 do-not-accord).\n",   t-1, t-1);
    printf("- {0 ..%4d} b {0 ..%4d}\tprint DNB (transition^2 not-left-accord).\n", t-1, t-1);
    printf("- {0 ..%4d} e {0 ..%4d}\tprint NES (guard-trans necessary enabling set).\n", l-1, t-1);
    printf("- {0 ..%4d} d {0 ..%4d}\tprint NDS (guard-trans necessary disabling set).\n",l-1, t-1);
    printf("- {0 ..%4d} m {0 ..%4d}\tprint MDS (guard-trans must disable set).\n", l-1, t-1);
    printf("- {0 ..%4d} c {0 ..%4d}\tprint MCE (guard^2 may-be coenabled).\n",     l-1, l-1);
    printf("- {0 ..%4d} t {0 ..%4d}\tprint GDM (guard-slot test set).\n",          l-1, s-1);
    printf("- {0 ..%4d} r {0 ..%4d}\tprint A2R (actions read set).\n",             t-1, s-1);
    printf("- {0 ..%4d} R {0 ..%4d}\tprint T2R (transition read set).\n",          t-1, s-1);
    printf("- {0 ..%4d} w {0 ..%4d}\tprint T2W (transition write set).\n",         t-1, s-1);
    printf("&\n");
    printf("* Combine commands to multiply matrices, e.g. '4ge' (get the NESs for a transition 4).\n");
//    printf("* Inverse order to reverse operation, e.g.: g5 (all transitions that have guard 5).\n");
    printf("* Use a minus sign anywhere to invert matrices, e.g.: '-4d-e' (all the NESs for guards that have transition 4 in an NDS).\n");
    printf("  Note that the minus sign is a switch, e.g.: in '-6ge' both G_T and NES are inverted.\n");
    printf("\n");
    printf("Enter a command:\n");
}

// TODO: type check (now only range checks, but nr guards may equal nr transitions)
// TODO: 'ge4' should be the same as '4-eg', i.e. print all transitions that have transition 4 in a NES (now it only checks whether one exits)
// TODO: pretty print (see io_print; apply should store results as set or lists or lists of lists)
// TODO: identify empty NESs? (counter end-object)
// TODO: auto use matrices

static inline bool
spins_check (int num, int min, int max)
{
    if (!(num >= min && num < max)) {
        printf("Out of range: %d (min %d, max %d)\n", num, min, max-1);
        return true;
    }
    return false;
}

static bool SPINS_DEBUG = true;

typedef struct visitor_s visitor_t;

typedef void (*print_f)(visitor_t *visitor);
typedef void (*object_f)(visitor_t *visitor);
typedef bool  (*apply_f)(visitor_t *visitor, int arg);

typedef struct matrix_s {
    int            *dm;
    int             rows;
    int             cols;
    bool            invert;
    char            c;
} matrix_t;

struct visitor_s {
    void           *ctx;
    apply_f         apply;
    print_f         print;
    object_f        object;
    visitor_t      *next;
    visitor_t      *prev;
    int             in_dim;
    int             out_dim;
    char           *name;
};

bool
io_end (visitor_t *this, int v)
{
    (void) this;
    printf("%d, ", v);
    return false;
}

bool
io_end2 (visitor_t *this, int v)
{
    if ((long)this->ctx == v) {
        printf ("true");
        return true; // shortcut
    }
    return false;
}

bool
io_start (visitor_t *this, int v)
{
    (void) v;
    this->next->apply(this->next, (long)this->ctx);
    return false;
}

bool
io_start_forall(visitor_t *this, int x)
{
    (void) x;
    int i;
    for (i = 0; i < this->out_dim; i++) {
        if (this->next->apply(this->next, i)) return true;
    }
    return false;
}

void io_object(visitor_t *this) {
    char *name = this->out_dim != -1 ? "start" : "end";
    printf("%s(%d)", name, (long)this->ctx);
}

void no_print(visitor_t *this) { (void) this; }

visitor_t *
io_create_end ()
{
    visitor_t *visitor = calloc(sizeof(visitor_t), 1);
    visitor->ctx = (void *) (long) 0;
    visitor->apply = io_end;
    visitor->object = io_object;
    visitor->print = no_print;
    visitor->in_dim  = 0;
    visitor->out_dim =  -1;
    return visitor;
}

visitor_t *
io_create_end2 (int v)
{
    visitor_t *visitor = calloc(sizeof(visitor_t), 1);
    visitor->ctx = (void *) (long) v;
    visitor->apply = io_end2;
    visitor->object = io_object;
    visitor->print = no_print;
    visitor->in_dim  = 0;
    visitor->out_dim =  -1;
    return visitor;
}

visitor_t *
io_create_start (int v)
{
    visitor_t *visitor = calloc(sizeof(visitor_t), 1);
    visitor->ctx = (void *) (long) v;
    visitor->apply = io_start;
    visitor->object = io_object;
    visitor->in_dim  = -1;
    visitor->out_dim =  0;
    return visitor;
}

visitor_t *
io_create_forall (int v)
{
    visitor_t *visitor = calloc(sizeof(visitor_t), 1);
    visitor->ctx = (void *) (long) v;
    visitor->apply = io_start_forall;
    visitor->object = io_object;
    visitor->in_dim  = -1;
    visitor->out_dim = v;
    return visitor;
}

bool
matrix_visit_row (visitor_t *this, int row) // todo: merge with col usei invert
{
    matrix_t *matrix = (matrix_t *) this->ctx;
    int i;
    if (spins_check(row, 0, matrix->rows)) return true;
    for (i = 0; i < matrix->cols; i++) {
        if (matrix->dm[matrix->cols * row + i]) {
            if (this->next->apply(this->next, i)) return true;
        }
    }
    return false;
}

bool
matrix_visit_col(visitor_t *this, int col)
{
    matrix_t *matrix = (matrix_t *) this->ctx;
    int i;
    if (spins_check(col, 0, matrix->cols)) return true;
    for (i = 0; i < matrix->rows; i++) {
        if (matrix->dm[matrix->cols * i + col]) {
            if (this->next->apply(this->next, i)) return true;
        }
    }
    return false;
}

void matrix_object(visitor_t *this) {
    matrix_t *matrix = (matrix_t *) this->ctx;
    printf("matrix%s(%c)", matrix->invert ? "-1" : "", matrix->c);
}

void matrix_print(visitor_t *this) {
    matrix_t *matrix = (matrix_t *) this->ctx;

}

visitor_t *
spins_create_matrix_visitor (int *dm, int rows, int cols, bool invert, char c)
{
    visitor_t *visitor = calloc(sizeof(visitor_t), 1);
    matrix_t *matrix = calloc(sizeof(matrix_t), 1);
    matrix->rows = rows;
    matrix->cols = cols;
    matrix->dm = dm;
    matrix->c = c;
    matrix->invert = invert;
    visitor->ctx = matrix;
    visitor->object = matrix_object;
    visitor->in_dim = invert ? cols : rows;
    visitor->out_dim = invert ? rows : cols;
    visitor->apply = invert ? matrix_visit_col : matrix_visit_row;
    //if (SPINS_DEBUG) matrix_print(visitor);
    return visitor;
}

visitor_t *
spins_create_matrix_visitor2 (char c, bool invert)
{
    int l = spins_get_label_count();
    int g = spins_get_guard_count();
    int t = spins_get_transition_groups();
    int s = spins_get_state_size();

    switch(c) {
    case 'g': return spins_create_matrix_visitor(t2g, t, l, invert, c);
    case 'e': return spins_create_matrix_visitor(nes_dm[0], l, t, invert, c);
    case 'd': return spins_create_matrix_visitor(nds_dm[0], l, t, invert, c);
    case 'u': return spins_create_matrix_visitor(commutes_dm[0], t, t, invert, c);
    case 'a': return spins_create_matrix_visitor(dna_dm[0], t, t, invert, c);
    case 'A': return spins_create_matrix_visitor(dna, t, t, invert, c);
    case 'b': return spins_create_matrix_visitor(dnb, t, t, invert, c);
    case 'c': return spins_create_matrix_visitor(co_dm[0], t, t, invert, c);
    case 't': return spins_create_matrix_visitor(gm_dm[0], t, t, invert, c);
    case 'm': return spins_create_matrix_visitor(dm_must_disable[0], l, t, invert, c);
    case 'r': return spins_create_matrix_visitor(actions_read_dependency[0], t, s, invert, c);
    case 'R': return spins_create_matrix_visitor(t2r, t, s, invert, c);
    case 'w': return spins_create_matrix_visitor(t2w, t, s, invert, c);
    default: return NULL;
    }
}

void
spins_visitor_destroy (visitor_t *visitor)
{
    if (visitor->ctx != NULL) {
        if (((long)visitor->ctx) > 99999999) free (visitor->ctx); // reusing pointer
    }
    if (visitor->next != NULL) {
        spins_visitor_destroy (visitor->next);
    }
    free (visitor);
}

typedef struct printer_s {
    visitor_t      *first;
    visitor_t      *last;
} printer_t;

void
printer_reinit (printer_t *p)
{
    if (p->first != NULL)
        spins_visitor_destroy (p->first);
    p->first = p->last = NULL;
}

bool
printer_chain (printer_t *p, visitor_t *v)
{
    if (p->first == NULL || p->last == NULL) {
        printf("Failed chaining "); v->object(v); printf(" (no chain)\n");
        return true;
    }
    if (p->last->out_dim != v->in_dim && p->last->out_dim != 0 && v->in_dim != 0) {
        printf ("Failed chaining "); v->object(v); printf(" to ");
        p->last->object(p->last); printf(" (dimension mismatch: %d ~ %d)\n", p->last->out_dim, v->in_dim);
        return true;
    }
    visitor_t *prev = p->last;
    p->last = p->last->next = v;
    p->last->prev = prev;
    return false;
}

bool
printer_add (printer_t *p, int val)
{
    if (p->first == NULL) {
        p->first = p->last = io_create_start(val);
    } else {
        visitor_t *v = io_create_end2(val);
        if (printer_chain(p, v)) {
            return true;
        }
    }
    return false; // ok
}

#include <ctype.h>

bool
printer_add2 (printer_t *p, char c, bool invert)
{
    if (!isalpha(c)) return false;
    visitor_t *v = spins_create_matrix_visitor2 (c, invert);
    if (v == NULL) {
        printf ("Wrong command: '%c'\n", c);
        return true;
    }

    if (p->first == NULL) { // create default forall visitor suited for this matrix
        p->first = p->last = io_create_forall(v->in_dim);
    }
    if (p->last->out_dim == -1) {
        printf ("Failed creating "); p->last->object(p->last); printf(" (previous command provides no input)\n");
        return true;
    }

    if (printer_chain(p, v))
        return true;
    return false; // ok
}

bool
printer_print (printer_t *p)
{
    if (p->first == NULL) return false; // ok: empty command

    if (p->first->out_dim == -1) {
        printf("Failed executing "); p->first->object(p->last); printf(" (first command provides no output)\n");
        return true;
    }

    if (p->last->out_dim != -1) { // add default visitor
        visitor_t *v = io_create_end(); // prints all
        if (printer_chain(p, v)) {
            return true;
        }
    }

    // do stuff
    bool premature = p->first->apply (p->first, -1);
    if (SPINS_DEBUG) printf("%s\n", premature ? " (shortcutted)" : "");

    if (p->last->print == NULL) {
        printf("Failed printing "); p->last->object(p->last); printf(" (last command provides no print function)\n");
        return true;
    }
    p->last->print (p->last);
    return false;
}

void
spins_query()
{
    spins_create_matrices();

use:spins_use_query();

    printer_t p;
    p.first = p.last = NULL;
    while (true) { // parse
        size_t len;
        char *in = fgetln(stdin, &len);
        if (feof(stdin) || ferror(stdin)) break;
        if (len == 0) continue;

        printer_reinit(&p);
        bool invert = false;
        char *end = in, *term = in + len;
        while (end != term) {
            if (end[0] == '-') {
                invert = !invert;
                end++;
                continue;
            }

            in = end;
            int v = strtol(in, &end, 10);
            if (end != in) { // parsed number

                if (printer_add(&p, v)) goto use; // may fail

            } else { // must be a command:
                char command = end++[0];

                if (printer_add2(&p, command, invert)) goto use; // may fail
            }
        }

        if (printer_print(&p)) goto use;
    }

    printer_reinit(&p); // destroy stuff
    free (t2g);
    free (dnb);
}

int
main(int argc, char **argv)
{
    spins_query();
}

typedef struct spins_args_s {
    void               *model;
    void (*callback)(void* arg, transition_info_t *transition_info, state_t *out);
    void               *arg;
    size_t              outs;
    int                 sid;
    transition_info_t  *ti_orig;
    void               *table;
} spins_args_t;

extern void spins_simple_dfs (spins_args_t *args, state_t *state, int atomic);

void
spins_simple_atomic_cb (void* arg, transition_info_t *transition_info, state_t *out, int atomic)
{
    spins_args_t *args = (spins_args_t *)arg;
    if (leaves_atomic[transition_info->group]) {
        args->callback (args->arg, args->ti_orig, out);
        args->outs++;
    } else {
        spins_simple_dfs (args, out, atomic);
    }
}

void
spins_simple_dfs (spins_args_t *args, state_t *state, int atomic)
{
    state_t out;
    int count = spins_get_successor_sid (args->model, state, args, &out, atomic);
    if (count == 0) {
        args->callback (args->arg, args->ti_orig, state);
        args->outs++;
    }
}

inline int
spins_simple_reach (void* model, transition_info_t *transition_info, state_t *in,
       void (*callback)(void* arg, transition_info_t *transition_info, state_t *out),
       void *arg, int sid) {
    spins_args_t args;
    args.table = NULL;
    args.model = model;
    args.callback = callback;
    args.arg = arg;
    args.outs = 0;
    args.sid = sid;
    args.ti_orig = transition_info;
    spins_simple_dfs (&args, in, sid);
    return args.outs;
}
