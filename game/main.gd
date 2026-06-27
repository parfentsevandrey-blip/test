extends Node2D
# ============================================================================
#  BRICK STORM  —  original brick-breaker arcade (Godot 4.3)
#  100% original code & procedural graphics. No third-party assets.
#  All upgrades are FREE by design — there are no in-app purchases.
# ============================================================================

# ---- Layout constants (design resolution 720 x 1280, portrait) -------------
const SCREEN_W := 720.0
const SCREEN_H := 1280.0
const COLS := 7
const CELL := SCREEN_W / COLS          # ~102.857 px square cells
const CEIL_Y := 168.0                  # ceiling balls bounce off
const TOP_MARGIN := 196.0              # first brick row top
const FLOOR_Y := 1132.0               # launch line / collection floor

const BALL_RADIUS := 11.0
const BALL_SPEED := 1350.0
const LAUNCH_DELAY := 0.05             # seconds between consecutive balls
const PICKUP_RADIUS := 17.0
const DESCEND_TIME := 0.22             # row slide-down animation

enum State { MENU, AIM, FIRE, RESOLVE, GAMEOVER, UPGRADES }

# ---- Runtime state ---------------------------------------------------------
var state := State.MENU
var font: Font

var bricks: Array = []                 # each: {col:int, row:int, hp:int, max_hp:int}
var pickups: Array = []                # each: {col:int, row:int, taken:bool}
var balls: Array = []                  # each: {pos:Vector2, vel:Vector2, alive:bool}

var level := 0
var best := 0
var run_balls := 0                     # balls available this run
var extra_balls := 0                   # pickups collected this turn (added at end)
var power := 1

var launch_x := SCREEN_W * 0.5
var next_launch_x := -1.0
var balls_to_spawn := 0
var spawn_timer := 0.0
var descend_offset := 0.0
var pending_gameover := false

var aiming := false
var aim_point := Vector2(SCREEN_W * 0.5, 400.0)

# ---- Persistent upgrades (all free to apply) -------------------------------
var up_balls := 0                      # +1 starting ball each level
var up_power := 0                      # +1 damage per hit each level
const UP_MAX := 15
const SAVE_PATH := "user://brickstorm_save.json"

# ---- UI rects --------------------------------------------------------------
var play_btn := Rect2(160, 560, 400, 120)
var upgrades_btn := Rect2(160, 720, 400, 100)
var back_btn := Rect2(40, 1140, 200, 90)
var up_balls_plus := Rect2(540, 470, 120, 100)
var up_power_plus := Rect2(540, 640, 120, 100)


func _ready() -> void:
	randomize()
	font = ThemeDB.fallback_font
	_load_game()


# ============================================================================
#  MAIN LOOP
# ============================================================================
func _process(delta: float) -> void:
	match state:
		State.FIRE:
			_update_fire(delta)
		State.RESOLVE:
			_update_resolve(delta)
	queue_redraw()


func _update_fire(delta: float) -> void:
	# spawn balls one at a time
	spawn_timer -= delta
	if balls_to_spawn > 0 and spawn_timer <= 0.0:
		_spawn_ball()
		balls_to_spawn -= 1
		spawn_timer = LAUNCH_DELAY

	var any_alive := false
	for b in balls:
		if b.alive:
			b.age += delta
			# anti-stuck: balls that loiter too long get pulled toward the floor
			if b.age > 6.0:
				var sp: float = b.vel.length()
				b.vel.y += 700.0 * delta
				b.vel = b.vel.normalized() * sp
			_step_ball(b, delta)
			if b.alive:
				any_alive = true

	if balls_to_spawn == 0 and not any_alive:
		_end_turn()


func _update_resolve(delta: float) -> void:
	descend_offset = max(0.0, descend_offset - (CELL / DESCEND_TIME) * delta)
	if descend_offset <= 0.0:
		if pending_gameover:
			_game_over()
		else:
			state = State.AIM


# ============================================================================
#  TURN / LEVEL LOGIC
# ============================================================================
func _start_run() -> void:
	bricks.clear()
	pickups.clear()
	balls.clear()
	level = 0
	power = 1 + up_power
	run_balls = 4 + up_balls
	extra_balls = 0
	launch_x = SCREEN_W * 0.5
	pending_gameover = false
	descend_offset = 0.0
	_advance_rows()        # seed the first row(s)
	_advance_rows()
	state = State.AIM


func _fire() -> void:
	balls_to_spawn = run_balls
	spawn_timer = 0.0
	extra_balls = 0
	next_launch_x = -1.0
	state = State.FIRE


func _spawn_ball() -> void:
	var dir := (aim_point - Vector2(launch_x, FLOOR_Y)).normalized()
	if dir.y > -0.18:                  # force a meaningfully upward shot
		dir.y = -0.18
		dir = dir.normalized()
	balls.append({
		"pos": Vector2(launch_x, FLOOR_Y - BALL_RADIUS),
		"vel": dir * BALL_SPEED,
		"alive": true,
		"age": 0.0,
	})


func _end_turn() -> void:
	run_balls += extra_balls
	extra_balls = 0
	balls.clear()
	if next_launch_x >= 0.0:
		launch_x = clampf(next_launch_x, BALL_RADIUS, SCREEN_W - BALL_RADIUS)
	_advance_rows()
	descend_offset = CELL
	state = State.RESOLVE


func _advance_rows() -> void:
	level += 1
	# push every existing brick / pickup down one row
	for b in bricks:
		b.row += 1
	for p in pickups:
		p.row += 1
	_spawn_row()
	# game over if anything now reaches the floor
	for b in bricks:
		if TOP_MARGIN + b.row * CELL + CELL > FLOOR_Y + 1.0:
			pending_gameover = true
			break


func _spawn_row() -> void:
	# choose which columns get a brick (leave at least one gap to pass through)
	var cols := range(COLS)
	cols.shuffle()
	var count := randi_range(3, 5)
	var chosen := cols.slice(0, count)
	for c in chosen:
		var hp := _roll_hp()
		bricks.append({"col": c, "row": 0, "hp": hp, "max_hp": hp})
	# spawn an extra-ball pickup in a free column ~70% of the time
	if randf() < 0.7:
		for c in cols:
			if not (c in chosen):
				pickups.append({"col": c, "row": 0, "taken": false})
				break


func _roll_hp() -> int:
	# gentle ramp: early levels stay easy, difficulty climbs slowly
	var cap := 1 + int(ceil(level * 0.7))
	var hp := randi_range(1, maxi(2, cap))
	# occasional tougher brick for variety
	if randf() < 0.12:
		hp = int(hp * 1.8) + 1
	return maxi(1, hp)


func _game_over() -> void:
	if level - 1 > best:
		best = level - 1
		_save_game()
	state = State.GAMEOVER


# ============================================================================
#  BALL PHYSICS  (manual, sub-stepped to prevent tunnelling)
# ============================================================================
func _step_ball(b: Dictionary, delta: float) -> void:
	var dist: float = b.vel.length() * delta
	var step_len := BALL_RADIUS * 0.5
	var n := maxi(1, int(ceil(dist / step_len)))
	var dt := delta / n
	for _i in range(n):
		if not b.alive:
			return
		b.pos += b.vel * dt
		_collide_walls(b)
		_collide_bricks(b)
		_collide_pickups(b)
		_check_floor(b)


func _collide_walls(b: Dictionary) -> void:
	if b.pos.x < BALL_RADIUS:
		b.pos.x = BALL_RADIUS
		b.vel.x = absf(b.vel.x)
	elif b.pos.x > SCREEN_W - BALL_RADIUS:
		b.pos.x = SCREEN_W - BALL_RADIUS
		b.vel.x = -absf(b.vel.x)
	if b.pos.y < CEIL_Y + BALL_RADIUS:
		b.pos.y = CEIL_Y + BALL_RADIUS
		b.vel.y = absf(b.vel.y)


func _collide_bricks(b: Dictionary) -> void:
	for i in range(bricks.size()):
		var br: Dictionary = bricks[i]
		var rect := _brick_rect(br)
		var closest: Vector2 = b.pos.clamp(rect.position, rect.position + rect.size)
		var diff: Vector2 = b.pos - closest
		var d: float = diff.length()
		if d < BALL_RADIUS:
			var normal: Vector2
			if d > 0.0001:
				normal = diff / d
			else:
				normal = Vector2.UP
			b.pos = closest + normal * BALL_RADIUS
			if b.vel.dot(normal) < 0.0:
				b.vel = b.vel - 2.0 * b.vel.dot(normal) * normal
			br.hp -= power
			if br.hp <= 0:
				bricks.remove_at(i)
			return     # at most one brick per sub-step


func _collide_pickups(b: Dictionary) -> void:
	for p in pickups:
		if p.taken:
			continue
		if b.pos.distance_to(_pickup_center(p)) < BALL_RADIUS + PICKUP_RADIUS:
			p.taken = true
			extra_balls += 1     # passes through, no bounce


func _check_floor(b: Dictionary) -> void:
	if b.pos.y + BALL_RADIUS >= FLOOR_Y and b.vel.y > 0.0:
		b.alive = false
		b.pos.y = FLOOR_Y - BALL_RADIUS
		if next_launch_x < 0.0:
			next_launch_x = clampf(b.pos.x, BALL_RADIUS, SCREEN_W - BALL_RADIUS)


# ============================================================================
#  GEOMETRY HELPERS
# ============================================================================
func _brick_rect(br: Dictionary) -> Rect2:
	var pad := 4.0
	var x: float = br.col * CELL + pad
	var y: float = TOP_MARGIN + br.row * CELL - descend_offset + pad
	return Rect2(x, y, CELL - pad * 2.0, CELL - pad * 2.0)


func _pickup_center(p: Dictionary) -> Vector2:
	var x: float = p.col * CELL + CELL * 0.5
	var y: float = TOP_MARGIN + p.row * CELL - descend_offset + CELL * 0.5
	return Vector2(x, y)


# ============================================================================
#  INPUT
# ============================================================================
func _input(event: InputEvent) -> void:
	var pressed := false
	var released := false
	var pos := Vector2.ZERO
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		pos = event.position
		pressed = event.pressed
		released = not event.pressed
	elif event is InputEventMouseMotion:
		pos = event.position
		if aiming and state == State.AIM:
			aim_point = pos
		return
	else:
		return

	match state:
		State.MENU:
			if released:
				if play_btn.has_point(pos):
					_start_run()
				elif upgrades_btn.has_point(pos):
					state = State.UPGRADES
		State.UPGRADES:
			if released:
				if back_btn.has_point(pos):
					state = State.MENU
				elif up_balls_plus.has_point(pos) and up_balls < UP_MAX:
					up_balls += 1
					_save_game()
				elif up_power_plus.has_point(pos) and up_power < UP_MAX:
					up_power += 1
					_save_game()
		State.AIM:
			if pressed and pos.y < FLOOR_Y - 10.0:
				aiming = true
				aim_point = pos
			elif released and aiming:
				aiming = false
				_fire()
		State.GAMEOVER:
			if released:
				state = State.MENU


# ============================================================================
#  RENDERING  (everything procedural)
# ============================================================================
func _draw() -> void:
	# backdrop
	draw_rect(Rect2(0, 0, SCREEN_W, SCREEN_H), Color(0.07, 0.09, 0.13), true)
	match state:
		State.MENU:
			_draw_menu()
		State.UPGRADES:
			_draw_upgrades()
		_:
			_draw_game()
			if state == State.GAMEOVER:
				_draw_gameover()


func _draw_game() -> void:
	# play-field frame
	draw_line(Vector2(0, CEIL_Y), Vector2(SCREEN_W, CEIL_Y), Color(0.25, 0.3, 0.4), 2.0)
	draw_line(Vector2(0, FLOOR_Y), Vector2(SCREEN_W, FLOOR_Y), Color(0.3, 0.35, 0.45), 3.0)

	# bricks
	for br in bricks:
		var r := _brick_rect(br)
		var col := _hp_color(br.hp)
		draw_rect(r, col, true)
		draw_rect(r, col.lightened(0.25), false, 2.0)
		_draw_centered(str(br.hp), r.get_center() + Vector2(0, 1), 30, Color.WHITE)

	# pickups (extra ball)
	for p in pickups:
		if p.taken:
			continue
		var c := _pickup_center(p)
		draw_circle(c, PICKUP_RADIUS, Color(0.2, 0.85, 0.55))
		draw_circle(c, PICKUP_RADIUS - 4.0, Color(0.1, 0.5, 0.35))
		_draw_centered("+", c + Vector2(0, 1), 24, Color.WHITE)

	# balls
	for b in balls:
		if b.alive:
			draw_circle(b.pos, BALL_RADIUS, Color(1.0, 0.93, 0.55))

	# aim guide
	if state == State.AIM and aiming:
		_draw_aim()

	# launcher
	if state == State.AIM:
		draw_circle(Vector2(launch_x, FLOOR_Y - 2.0), BALL_RADIUS + 2.0, Color(1.0, 0.93, 0.55))

	_draw_hud()


func _draw_aim() -> void:
	var origin := Vector2(launch_x, FLOOR_Y - BALL_RADIUS)
	var dir := (aim_point - origin).normalized()
	if dir.y > -0.18:
		dir.y = -0.18
		dir = dir.normalized()
	var p := origin
	var v := dir
	var dot_gap := 26.0
	var traveled := 0.0
	var bounces := 0
	for _i in range(220):
		p += v * 6.0
		traveled += 6.0
		# wall reflections for the preview
		if p.x < BALL_RADIUS:
			p.x = BALL_RADIUS
			v.x = absf(v.x)
			bounces += 1
		elif p.x > SCREEN_W - BALL_RADIUS:
			p.x = SCREEN_W - BALL_RADIUS
			v.x = -absf(v.x)
			bounces += 1
		if p.y < CEIL_Y + BALL_RADIUS:
			break
		# stop at first brick
		var hit := false
		for br in bricks:
			if _brick_rect(br).grow(BALL_RADIUS).has_point(p):
				hit = true
				break
		if hit or bounces > 3:
			break
		if fmod(traveled, dot_gap) < 6.0:
			draw_circle(p, 3.0, Color(1, 1, 1, 0.5))


func _draw_hud() -> void:
	_draw_text("LEVEL " + str(level), Vector2(24, 60), 40, Color.WHITE, HORIZONTAL_ALIGNMENT_LEFT)
	_draw_text("BEST " + str(best), Vector2(SCREEN_W - 24, 60), 30, Color(0.7, 0.75, 0.85), HORIZONTAL_ALIGNMENT_RIGHT)
	var ball_txt := "BALLS x" + str(run_balls)
	if extra_balls > 0:
		ball_txt += " (+" + str(extra_balls) + ")"
	_draw_text(ball_txt, Vector2(24, 110), 30, Color(1.0, 0.93, 0.55), HORIZONTAL_ALIGNMENT_LEFT)
	if state == State.AIM:
		_draw_text("drag to aim — release to fire", Vector2(SCREEN_W * 0.5, FLOOR_Y + 64),
			26, Color(0.6, 0.65, 0.75), HORIZONTAL_ALIGNMENT_CENTER)


func _draw_menu() -> void:
	_draw_text("BRICK", Vector2(SCREEN_W * 0.5, 280), 130, Color(1.0, 0.93, 0.55), HORIZONTAL_ALIGNMENT_CENTER)
	_draw_text("STORM", Vector2(SCREEN_W * 0.5, 410), 130, Color(0.3, 0.85, 0.7), HORIZONTAL_ALIGNMENT_CENTER)
	_draw_text("BEST  " + str(best), Vector2(SCREEN_W * 0.5, 490), 36, Color(0.7, 0.75, 0.85), HORIZONTAL_ALIGNMENT_CENTER)
	_draw_button(play_btn, "PLAY", Color(0.2, 0.7, 0.5))
	_draw_button(upgrades_btn, "UPGRADES", Color(0.3, 0.4, 0.6))
	_draw_text("All upgrades are 100% FREE", Vector2(SCREEN_W * 0.5, 900), 28, Color(0.55, 0.85, 0.6), HORIZONTAL_ALIGNMENT_CENTER)
	_draw_text("no ads — no in-app purchases", Vector2(SCREEN_W * 0.5, 945), 24, Color(0.5, 0.55, 0.65), HORIZONTAL_ALIGNMENT_CENTER)


func _draw_upgrades() -> void:
	_draw_text("UPGRADES", Vector2(SCREEN_W * 0.5, 160), 70, Color.WHITE, HORIZONTAL_ALIGNMENT_CENTER)
	_draw_text("everything here is FREE", Vector2(SCREEN_W * 0.5, 230), 28, Color(0.55, 0.85, 0.6), HORIZONTAL_ALIGNMENT_CENTER)

	_draw_upgrade_row("Starting balls", 4 + up_balls, up_balls, up_balls_plus, 470)
	_draw_upgrade_row("Ball power", 1 + up_power, up_power, up_power_plus, 640)

	_draw_button(back_btn, "BACK", Color(0.4, 0.4, 0.5))


func _draw_upgrade_row(title: String, value: int, lvl: int, plus_rect: Rect2, y: float) -> void:
	draw_rect(Rect2(40, y - 10, 480, 100), Color(0.12, 0.15, 0.2), true)
	_draw_text(title, Vector2(64, y + 38), 34, Color.WHITE, HORIZONTAL_ALIGNMENT_LEFT)
	_draw_text("now: " + str(value) + "   (lvl " + str(lvl) + "/" + str(UP_MAX) + ")",
		Vector2(64, y + 78), 24, Color(0.7, 0.75, 0.85), HORIZONTAL_ALIGNMENT_LEFT)
	if lvl < UP_MAX:
		_draw_button(plus_rect, "+ FREE", Color(0.2, 0.7, 0.5), 26)
	else:
		_draw_button(plus_rect, "MAX", Color(0.35, 0.35, 0.4), 26)


func _draw_gameover() -> void:
	draw_rect(Rect2(0, 0, SCREEN_W, SCREEN_H), Color(0, 0, 0, 0.6), true)
	_draw_text("GAME OVER", Vector2(SCREEN_W * 0.5, 520), 90, Color(1.0, 0.5, 0.4), HORIZONTAL_ALIGNMENT_CENTER)
	_draw_text("Reached level " + str(level - 1), Vector2(SCREEN_W * 0.5, 600), 40, Color.WHITE, HORIZONTAL_ALIGNMENT_CENTER)
	_draw_text("Best  " + str(best), Vector2(SCREEN_W * 0.5, 660), 34, Color(0.7, 0.75, 0.85), HORIZONTAL_ALIGNMENT_CENTER)
	_draw_text("tap to continue", Vector2(SCREEN_W * 0.5, 780), 30, Color(0.6, 0.65, 0.75), HORIZONTAL_ALIGNMENT_CENTER)


# ---- small draw helpers ----------------------------------------------------
func _hp_color(hp: int) -> Color:
	var t := clampf(float(hp) / 24.0, 0.0, 1.0)
	return Color.from_hsv(lerpf(0.42, 0.0, t), 0.65, 0.92)


func _draw_button(r: Rect2, label: String, col: Color, size := 40) -> void:
	draw_rect(r, col, true)
	draw_rect(r, col.lightened(0.3), false, 2.0)
	_draw_centered(label, r.get_center(), size, Color.WHITE)


func _draw_centered(text: String, center: Vector2, size: int, col: Color) -> void:
	var w := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, size).x
	var asc := font.get_ascent(size)
	var desc := font.get_descent(size)
	var pos := Vector2(center.x - w * 0.5, center.y + (asc - desc) * 0.5)
	draw_string(font, pos, text, HORIZONTAL_ALIGNMENT_LEFT, -1, size, col)


func _draw_text(text: String, pos: Vector2, size: int, col: Color, align: int) -> void:
	var p := pos
	if align == HORIZONTAL_ALIGNMENT_CENTER:
		var w := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, size).x
		p.x -= w * 0.5
	elif align == HORIZONTAL_ALIGNMENT_RIGHT:
		var w2 := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, size).x
		p.x -= w2
	draw_string(font, p, text, HORIZONTAL_ALIGNMENT_LEFT, -1, size, col)


# ============================================================================
#  SAVE / LOAD
# ============================================================================
func _save_game() -> void:
	var data := {"best": best, "up_balls": up_balls, "up_power": up_power}
	var f := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(data))
		f.close()


func _load_game() -> void:
	if not FileAccess.file_exists(SAVE_PATH):
		return
	var f := FileAccess.open(SAVE_PATH, FileAccess.READ)
	if f == null:
		return
	var parsed = JSON.parse_string(f.get_as_text())
	f.close()
	if typeof(parsed) == TYPE_DICTIONARY:
		best = int(parsed.get("best", 0))
		up_balls = int(parsed.get("up_balls", 0))
		up_power = int(parsed.get("up_power", 0))
