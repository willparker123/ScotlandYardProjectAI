public class Dog extends Animal {
    String name = "dog";

    @Override
    public String eat(Food food) {
        return "dog eats "+food.eaten(this);
    }
}
