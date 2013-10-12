
typedef struct spins_args_s {
	void			   *model;
	void (*callback)(void* arg, transition_info_t *transition_info, state_t *out);
	void 			   *arg;
	size_t 				outs;
	int 				sid;
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

